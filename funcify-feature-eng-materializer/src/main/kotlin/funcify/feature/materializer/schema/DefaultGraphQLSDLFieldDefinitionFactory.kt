package funcify.feature.materializer.schema

import arrow.core.Either
import arrow.core.Option
import arrow.core.left
import arrow.core.none
import arrow.core.right
import arrow.core.some
import arrow.core.toOption
import funcify.feature.datasource.sdl.SourceIndexGqlSdlDefinitionFactory
import funcify.feature.datasource.graphql.schema.GraphQLSourceAttribute
import funcify.feature.materializer.error.MaterializerErrorResponse
import funcify.feature.materializer.error.MaterializerException
import funcify.feature.schema.datasource.DataSourceType
import funcify.feature.schema.datasource.RawDataSourceType
import funcify.feature.schema.datasource.SourceAttribute
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.schema.index.CompositeAttribute
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.container.attempt.Try.Companion.filterInstanceOf
import funcify.feature.tools.control.TraversalFunctions
import funcify.feature.tools.extensions.FunctionExtensions.compose
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import graphql.language.Description
import graphql.language.FieldDefinition
import graphql.language.InputValueDefinition
import graphql.language.ListType
import graphql.language.NonNullType
import graphql.language.SourceLocation
import graphql.language.Type
import graphql.language.TypeName
import graphql.schema.*
import kotlin.reflect.KClass
import kotlin.reflect.full.isSuperclassOf
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList
import org.slf4j.Logger

internal class DefaultGraphQLSDLFieldDefinitionFactory(
    private val sourceIndexGqlSdlDefinitionFactories: List<SourceIndexGqlSdlDefinitionFactory<*>>
) : GraphQLSDLFieldDefinitionFactory {

    companion object {
        private val logger: Logger = loggerFor<DefaultGraphQLSDLFieldDefinitionFactory>()
    }

    private val supportedDataSourceTypes: Set<DataSourceType> by lazy {
        sourceIndexGqlSdlDefinitionFactories.fold(
            persistentSetOf<DataSourceType>(RawDataSourceType.GRAPHQL_API)
        ) { ps, factory -> ps.add(factory.dataSourceType) }
    }

    private val sourceIndexGqlSdlDefFactoryBySourceIndexType:
        ImmutableMap<KClass<out SourceIndex>, SourceIndexGqlSdlDefinitionFactory<*>> by lazy {
        sourceIndexGqlSdlDefinitionFactories.fold(persistentMapOf()) { pm, factory ->
            pm.put(factory.sourceIndexType, factory)
        }
    }

    private val cachingCompositeAttributeTypeToFactoryFunction:
        (CompositeAttribute) -> Option<
                Pair<SourceAttribute, SourceIndexGqlSdlDefinitionFactory<*>>> =
        createCachingFactoryForCompositeAttributeFunction()

    override fun createFieldDefinitionForCompositeAttribute(
        compositeAttribute: CompositeAttribute
    ): FieldDefinition {
        logger.debug(
            """create_field_definition_for_composite_attribute: [ 
                |composite_attribute.name: ${compositeAttribute.conventionalName} 
                |]""".flattenIntoOneLine()
        )
        return when {
            compositeAttribute.canBeSourcedFrom(RawDataSourceType.GRAPHQL_API) -> {
                Try.attempt { deriveFieldDefinitionFromGraphQLSourceAttribute(compositeAttribute) }
            }
            cachingCompositeAttributeTypeToFactoryFunction
                .invoke(compositeAttribute)
                .isDefined() -> {
                cachingCompositeAttributeTypeToFactoryFunction
                    .invoke(compositeAttribute)
                    .fold(
                        {
                            Try.failure(
                                MaterializerException(
                                    MaterializerErrorResponse.GRAPHQL_SCHEMA_CREATION_ERROR,
                                    """source_index_gql_sdl_definition_factory 
                                       |for source_attribute unexpectedly not found 
                                       |despite check beforehand
                                       |""".flattenIntoOneLine()
                                )
                            )
                        },
                        { (sa, factory) -> createFieldDefinitionUsingFactory(factory, sa) }
                    )
            }
            else -> {
                val supportedDataSourceTypesAsString: String =
                    supportedDataSourceTypes.joinToString(", ", "{ ", " }")
                val dataSourceKeysAsString =
                    compositeAttribute
                        .getSourceAttributeByDataSource()
                        .keys
                        .joinToString(", ", "{ ", " }")
                Try.failure(
                    MaterializerException(
                        MaterializerErrorResponse.GRAPHQL_SCHEMA_CREATION_ERROR,
                        """no supported data_source_types within 
                           |composite_attribute: [ name: ${compositeAttribute.conventionalName}, 
                           |data_source_keys: $dataSourceKeysAsString ] 
                           |[ supported_data_sources: $supportedDataSourceTypesAsString ] 
                           |""".flattenIntoOneLine()
                    )
                )
            }
        }.orElseThrow()
    }

    private fun <SI : SourceIndex> createFieldDefinitionUsingFactory(
        factory: SourceIndexGqlSdlDefinitionFactory<SI>,
        sourceAttribute: SourceAttribute
    ): Try<FieldDefinition> {
        @Suppress("UNCHECKED_CAST") //
        val sourceAttributeAsExpectedSourceIndex: SI = sourceAttribute as SI
        return factory
            .createGraphQLSDLNodeForSourceIndex(sourceAttributeAsExpectedSourceIndex)
            .filterInstanceOf()
    }

    private fun createCachingFactoryForCompositeAttributeFunction():
        (CompositeAttribute) -> Option<
                Pair<SourceAttribute, SourceIndexGqlSdlDefinitionFactory<*>>> {
        val sourceIndexSuperTypeBySourceIndexSubType: MutableMap<KClass<*>, KClass<*>> =
            mutableMapOf()
        return { ca: CompositeAttribute ->
            ca.getSourceAttributeByDataSource()
                .values
                .asSequence()
                .filter { sa: SourceAttribute ->
                    if (sa::class in sourceIndexSuperTypeBySourceIndexSubType) {
                        true
                    } else {
                        sourceIndexGqlSdlDefFactoryBySourceIndexType
                            .asSequence()
                            .filter { (siKCls, factory) -> siKCls.isSuperclassOf(sa::class) }
                            .firstOrNull()
                            .toOption()
                            .fold({}) { (siKCls, factory) ->
                                sourceIndexSuperTypeBySourceIndexSubType[sa::class] = siKCls
                            }
                        sa::class in sourceIndexSuperTypeBySourceIndexSubType
                    }
                }
                .flatMap { sa: SourceAttribute ->
                    sourceIndexSuperTypeBySourceIndexSubType[sa::class]
                        .toOption()
                        .flatMap { siKCls: KClass<*> ->
                            sourceIndexGqlSdlDefFactoryBySourceIndexType[siKCls].toOption()
                        }
                        .fold(::emptySequence, ::sequenceOf)
                        .map { factory: SourceIndexGqlSdlDefinitionFactory<*> -> sa to factory }
                }
                .firstOrNull()
                .toOption()
        }
    }

    private fun deriveFieldDefinitionFromGraphQLSourceAttribute(
        compositeAttribute: CompositeAttribute
    ): FieldDefinition {
        val firstAvailableGraphQLSourceAttribute: SourceAttribute =
            (compositeAttribute
                .getSourceAttributeByDataSource()
                .asSequence()
                .filter { entry -> entry.key.sourceType == RawDataSourceType.GRAPHQL_API }
                .map { entry -> entry.value }
                .firstOrNull()
                ?: throw MaterializerException(
                    MaterializerErrorResponse.GRAPHQL_SCHEMA_CREATION_ERROR,
                    "graphql_source_attribute reported available but not found"
                ))
        val graphQLFieldDefinitionOnThatSource: GraphQLFieldDefinition =
            when (firstAvailableGraphQLSourceAttribute) {
                is GraphQLSourceAttribute ->
                    firstAvailableGraphQLSourceAttribute.schemaFieldDefinition
                else ->
                    throw MaterializerException(
                        MaterializerErrorResponse.GRAPHQL_SCHEMA_CREATION_ERROR,
                        """graphql_source_attribute.type [ expected: 
                            |${GraphQLSourceAttribute::class.qualifiedName}, actual: 
                            |${firstAvailableGraphQLSourceAttribute::class.qualifiedName} 
                            |]""".flattenIntoOneLine()
                    )
            }
        return when (val sdlDefinition: FieldDefinition? =
                graphQLFieldDefinitionOnThatSource.definition
        ) {
            null -> {
                if (graphQLFieldDefinitionOnThatSource.description?.isNotEmpty() == true) {
                    FieldDefinition.newFieldDefinition()
                        .name(graphQLFieldDefinitionOnThatSource.name)
                        .description(
                            Description(
                                graphQLFieldDefinitionOnThatSource.description,
                                SourceLocation.EMPTY,
                                graphQLFieldDefinitionOnThatSource.description.contains('\n')
                            )
                        )
                        .type(
                            recursivelyDetermineSDLTypeForGraphQLInputOrOutputType(
                                graphQLInputOrOutputType = graphQLFieldDefinitionOnThatSource.type
                            )
                        )
                        .inputValueDefinitions(
                            extractInputValueDefinitionsFromArgumentsOnFieldDefinitionSource(
                                firstAvailableGraphQLSourceAttribute.sourcePath,
                                graphQLFieldDefinitionOnThatSource
                            )
                        )
                        .build()
                } else {
                    FieldDefinition.newFieldDefinition()
                        .name(graphQLFieldDefinitionOnThatSource.name)
                        .type(
                            recursivelyDetermineSDLTypeForGraphQLInputOrOutputType(
                                graphQLInputOrOutputType = graphQLFieldDefinitionOnThatSource.type
                            )
                        )
                        .build()
                }
            }
            else -> {
                sdlDefinition
            }
        }
    }

    private fun extractInputValueDefinitionsFromArgumentsOnFieldDefinitionSource(
        sourceAttributePath: SchematicPath,
        definition: GraphQLFieldDefinition
    ): List<InputValueDefinition> {
        return if (definition.arguments.isEmpty()) {
            emptyList()
        } else {
            definition
                .arguments
                .map { graphQLArgument: GraphQLArgument ->
                    InputValueDefinition.newInputValueDefinition()
                        .name(graphQLArgument.name)
                        .description(
                            Description(
                                sourceAttributePath
                                    .transform {
                                        argument(
                                            graphQLArgument.name,
                                            graphQLArgument.argumentDefaultValue.value?.toString()
                                                ?: ""
                                        )
                                    }
                                    .toString(),
                                SourceLocation.EMPTY,
                                false
                            )
                        )
                        .type(recursivelyDetermineSDLTypeForGraphQLInputOrOutputType(graphQLArgument.type))
                        .build()
                }
                .toPersistentList()
        }
    }

    private fun recursivelyDetermineSDLTypeForGraphQLInputOrOutputType(
        graphQLInputOrOutputType: GraphQLType
    ): Type<*> {
        if (graphQLInputOrOutputType !is GraphQLOutputType &&
                graphQLInputOrOutputType !is GraphQLInputType
        ) {
            val message =
                """the graphql_type passed in as input is 
                   |neither an input or output type 
                   |so an SDL Type<*> instance cannot be determined: 
                   |[ actual: ${graphQLInputOrOutputType::class.qualifiedName} 
                   |]""".flattenIntoOneLine()
            throw MaterializerException(
                MaterializerErrorResponse.GRAPHQL_SCHEMA_CREATION_ERROR,
                message
            )
        }
        val traversalFunction:
            (Pair<(Type<*>) -> Type<*>, GraphQLType>) -> Option<
                    Either<Pair<(Type<*>) -> Type<*>, GraphQLType>, Type<*>>> =
            { pair: Pair<(Type<*>) -> Type<*>, GraphQLType> ->
                when (val graphQLType: GraphQLType = pair.second) {
                    is GraphQLNonNull ->
                        (pair.first.compose<Type<*>, Type<*>, Type<*>> { t: Type<*> ->
                                NonNullType.newNonNullType().type(t).build()
                            } to graphQLType.wrappedType)
                            .left()
                            .some()
                    is GraphQLList ->
                        (pair.first.compose<Type<*>, Type<*>, Type<*>> { t: Type<*> ->
                                ListType.newListType().type(t).build()
                            } to graphQLType.wrappedType)
                            .left()
                            .some()
                    is GraphQLNamedType ->
                        pair.first
                            .invoke(TypeName.newTypeName(graphQLType.name).build())
                            .right()
                            .some()
                    else -> none()
                }
            }
        return TraversalFunctions.recurseWithOption(
                ({ t: Type<*> -> t } to graphQLInputOrOutputType),
                traversalFunction
            )
            .fold(
                { ->
                    throw unnamedGraphQLInputOrOutputTypeGraphQLSchemaCreationError(
                        graphQLInputOrOutputType
                    )
                },
                { t: Type<*> -> t }
            )
    }

    private fun unnamedGraphQLInputOrOutputTypeGraphQLSchemaCreationError(
        graphQLInputOrOutputType: GraphQLType
    ): MaterializerException {
        val inputOrOutputType: String =
            if (graphQLInputOrOutputType is GraphQLInputType) {
                "input_type"
            } else {
                "output_type"
            }
        return MaterializerException(
            MaterializerErrorResponse.GRAPHQL_SCHEMA_CREATION_ERROR,
            """graphql_field_definition.${inputOrOutputType} [ type.to_string: 
                |$graphQLInputOrOutputType ] 
                |does not have name for use in SDL type creation
                |""".flattenIntoOneLine()
        )
    }
}

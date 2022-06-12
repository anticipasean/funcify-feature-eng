package funcify.feature.materializer.schema

import arrow.core.Option
import arrow.core.toOption
import funcify.feature.datasource.gql.SourceIndexGqlSdlDefinitionFactory
import funcify.feature.datasource.graphql.schema.GraphQLSourceAttribute
import funcify.feature.materializer.error.MaterializerErrorResponse
import funcify.feature.materializer.error.MaterializerException
import funcify.feature.schema.datasource.DataSourceType
import funcify.feature.schema.datasource.RawDataSourceType
import funcify.feature.schema.datasource.SourceAttribute
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.schema.index.CompositeAttribute
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.container.attempt.Try.Companion.filterInstanceOf
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import graphql.language.Description
import graphql.language.FieldDefinition
import graphql.language.ListType
import graphql.language.NonNullType
import graphql.language.SourceLocation
import graphql.language.Type
import graphql.language.TypeName
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNamedOutputType
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLOutputType
import kotlin.reflect.KClass
import kotlin.reflect.full.isSuperclassOf
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
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
                            deriveSDLTypeFromGraphQLFieldDefinitionOutputType(
                                graphQLOutputType = graphQLFieldDefinitionOnThatSource.type
                            )
                        )
                        .build()
                } else {
                    FieldDefinition.newFieldDefinition()
                        .name(graphQLFieldDefinitionOnThatSource.name)
                        .type(
                            deriveSDLTypeFromGraphQLFieldDefinitionOutputType(
                                graphQLOutputType = graphQLFieldDefinitionOnThatSource.type
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

    fun deriveSDLTypeFromGraphQLFieldDefinitionOutputType(
        graphQLOutputType: GraphQLOutputType
    ): Type<*> {
        return when (graphQLOutputType) {
            is GraphQLNonNull -> {
                if (graphQLOutputType.wrappedType is GraphQLList) {
                    if ((graphQLOutputType.wrappedType as GraphQLList).wrappedType is GraphQLNonNull
                    ) {
                        NonNullType.newNonNullType(
                                ListType.newListType(
                                        NonNullType.newNonNullType(
                                                TypeName.newTypeName(
                                                        (((graphQLOutputType.wrappedType as?
                                                                        GraphQLList)
                                                                    ?.wrappedType as?
                                                                    GraphQLNonNull)
                                                                ?.wrappedType as?
                                                                GraphQLNamedOutputType)
                                                            ?.name
                                                            ?: throw MaterializerException(
                                                                MaterializerErrorResponse
                                                                    .GRAPHQL_SCHEMA_CREATION_ERROR,
                                                                "graphql_field_definition does not have name for use in SDL type creation"
                                                            )
                                                    )
                                                    .build()
                                            )
                                            .build()
                                    )
                                    .build()
                            )
                            .build()
                    } else {
                        NonNullType.newNonNullType(
                                ListType.newListType(
                                        TypeName.newTypeName(
                                                ((graphQLOutputType.wrappedType as? GraphQLList)
                                                        ?.wrappedType as?
                                                        GraphQLNamedOutputType)
                                                    ?.name
                                                    ?: throw MaterializerException(
                                                        MaterializerErrorResponse
                                                            .GRAPHQL_SCHEMA_CREATION_ERROR,
                                                        "graphql_field_definition does not have name for use in SDL type creation"
                                                    )
                                            )
                                            .build()
                                    )
                                    .build()
                            )
                            .build()
                    }
                } else {
                    NonNullType.newNonNullType(
                            TypeName.newTypeName(
                                    (graphQLOutputType.wrappedType as? GraphQLNamedOutputType)?.name
                                        ?: throw MaterializerException(
                                            MaterializerErrorResponse.GRAPHQL_SCHEMA_CREATION_ERROR,
                                            "graphql_field_definition does not have name for use in SDL type creation"
                                        )
                                )
                                .build()
                        )
                        .build()
                }
            }
            is GraphQLList -> {
                if (graphQLOutputType.wrappedType is GraphQLNonNull) {
                    ListType.newListType(
                            NonNullType.newNonNullType(
                                    TypeName.newTypeName(
                                            ((graphQLOutputType.wrappedType as? GraphQLNonNull)
                                                    ?.wrappedType as?
                                                    GraphQLNamedOutputType)
                                                ?.name
                                                ?: throw MaterializerException(
                                                    MaterializerErrorResponse
                                                        .GRAPHQL_SCHEMA_CREATION_ERROR,
                                                    "graphql_field_definition does not have name for use in SDL type creation"
                                                )
                                        )
                                        .build()
                                )
                                .build()
                        )
                        .build()
                } else {
                    ListType.newListType(
                            TypeName.newTypeName(
                                    (graphQLOutputType.wrappedType as? GraphQLNamedOutputType)?.name
                                        ?: throw MaterializerException(
                                            MaterializerErrorResponse.GRAPHQL_SCHEMA_CREATION_ERROR,
                                            "graphql_field_definition does not have name for use in SDL type creation"
                                        )
                                )
                                .build()
                        )
                        .build()
                }
            }
            is GraphQLNamedOutputType -> {
                TypeName.newTypeName(
                        graphQLOutputType.name
                            ?: throw MaterializerException(
                                MaterializerErrorResponse.GRAPHQL_SCHEMA_CREATION_ERROR,
                                "graphql_field_definition does not have name for use in SDL type creation"
                            )
                    )
                    .build()
            }
            else -> {
                throw MaterializerException(
                    MaterializerErrorResponse.GRAPHQL_SCHEMA_CREATION_ERROR,
                    "graphql_field_definition does not have name for use in SDL type creation"
                )
            }
        }
    }
}

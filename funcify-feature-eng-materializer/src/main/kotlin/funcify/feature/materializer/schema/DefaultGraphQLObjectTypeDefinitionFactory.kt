package funcify.feature.materializer.schema

import arrow.core.Option
import arrow.core.toOption
import funcify.feature.datasource.gql.SourceIndexGqlSdlDefinitionFactory
import funcify.feature.datasource.graphql.schema.GraphQLSourceContainerType
import funcify.feature.materializer.error.MaterializerErrorResponse
import funcify.feature.materializer.error.MaterializerException
import funcify.feature.schema.datasource.DataSourceType
import funcify.feature.schema.datasource.RawDataSourceType
import funcify.feature.schema.datasource.SourceContainerType
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.schema.index.CompositeContainerType
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.container.attempt.Try.Companion.filterInstanceOf
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import graphql.language.Description
import graphql.language.Node
import graphql.language.ObjectTypeDefinition
import graphql.language.SourceLocation
import graphql.schema.GraphQLFieldsContainer
import kotlin.reflect.KClass
import kotlin.reflect.full.isSuperclassOf
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import org.slf4j.Logger

internal class DefaultGraphQLObjectTypeDefinitionFactory(
    private val sourceIndexGqlSdlDefinitionFactories: List<SourceIndexGqlSdlDefinitionFactory<*>>
) : GraphQLObjectTypeDefinitionFactory {

    companion object {
        private val logger: Logger = loggerFor<DefaultGraphQLObjectTypeDefinitionFactory>()
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

    private val cachingCompositeContainerTypeTypeToFactoryFunction:
        (CompositeContainerType) -> Option<
                Pair<SourceContainerType<*>, SourceIndexGqlSdlDefinitionFactory<*>>> =
        createCachingFactoryForCompositeContainerTypeFunction()

    override fun createObjectTypeDefinitionForCompositeContainerType(
        compositeContainerType: CompositeContainerType
    ): ObjectTypeDefinition {
        logger.debug(
            """create_object_type_definition_for_composite_container_type: 
               |[ composite_container_type.name: ${compositeContainerType.conventionalName} 
               |]""".flattenIntoOneLine()
        )
        return when {
            compositeContainerType.canBeSourcedFrom(RawDataSourceType.GRAPHQL_API) -> {
                Try.attempt {
                    deriveObjectTypeDefinitionFromFirstAvailableGraphQLApiSourceContainerType(
                        compositeContainerType
                    )
                }
            }
            cachingCompositeContainerTypeTypeToFactoryFunction
                .invoke(compositeContainerType)
                .isDefined() -> {
                cachingCompositeContainerTypeTypeToFactoryFunction
                    .invoke(compositeContainerType)
                    .fold(
                        {
                            Try.failure(
                                MaterializerException(
                                    MaterializerErrorResponse.GRAPHQL_SCHEMA_CREATION_ERROR,
                                    """source_index_gql_sdl_definition_factory 
                                       |for source_container_type unexpectedly not found 
                                       |despite check beforehand
                                       |""".flattenIntoOneLine()
                                )
                            )
                        },
                        { (sct, factory) -> createObjectTypeDefinitionUsingFactory(factory, sct) }
                    )
            }
            else -> {
                val supportedDataSourceTypesAsString: String =
                    supportedDataSourceTypes.joinToString(", ", "{ ", " }")
                val dataSourceKeysAsString =
                    compositeContainerType
                        .getSourceContainerTypeByDataSource()
                        .keys
                        .joinToString(", ", "{ ", " }")
                Try.failure(
                    MaterializerException(
                        MaterializerErrorResponse.GRAPHQL_SCHEMA_CREATION_ERROR,
                        """no supported data_source_types within 
                           |composite_container_type: [ name: ${compositeContainerType.conventionalName}, 
                           |data_source_keys: $dataSourceKeysAsString ] 
                           |[ supported_data_sources: $supportedDataSourceTypesAsString ] 
                           |""".flattenIntoOneLine()
                    )
                )
            }
        }.orElseThrow()
    }

    private fun createCachingFactoryForCompositeContainerTypeFunction():
        (CompositeContainerType) -> Option<
                Pair<SourceContainerType<*>, SourceIndexGqlSdlDefinitionFactory<*>>> {
        val sourceIndexSuperTypeBySourceIndexSubType: MutableMap<KClass<*>, KClass<*>> =
            mutableMapOf()
        return { cct: CompositeContainerType ->
            cct.getSourceContainerTypeByDataSource()
                .values
                .asSequence()
                .filter { sct: SourceContainerType<*> ->
                    if (sct::class in sourceIndexSuperTypeBySourceIndexSubType) {
                        true
                    } else {
                        sourceIndexGqlSdlDefFactoryBySourceIndexType
                            .asSequence()
                            .filter { (siKCls, factory) -> siKCls.isSuperclassOf(sct::class) }
                            .firstOrNull()
                            .toOption()
                            .fold({}) { (siKCls, factory) ->
                                sourceIndexSuperTypeBySourceIndexSubType[sct::class] = siKCls
                            }
                        sct::class in sourceIndexSuperTypeBySourceIndexSubType
                    }
                }
                .flatMap { sct: SourceContainerType<*> ->
                    sourceIndexSuperTypeBySourceIndexSubType[sct::class]
                        .toOption()
                        .flatMap { siKCls: KClass<*> ->
                            sourceIndexGqlSdlDefFactoryBySourceIndexType[siKCls].toOption()
                        }
                        .fold(::emptySequence, ::sequenceOf)
                        .map { factory: SourceIndexGqlSdlDefinitionFactory<*> -> sct to factory }
                }
                .firstOrNull()
                .toOption()
        }
    }

    private fun <SI : SourceIndex> createObjectTypeDefinitionUsingFactory(
        factory: SourceIndexGqlSdlDefinitionFactory<SI>,
        sourceContainerType: SourceContainerType<*>
    ): Try<ObjectTypeDefinition> {
        @Suppress("UNCHECKED_CAST") //
        val sourceContainerTypeAsExpectedSourceIndexType: SI = sourceContainerType as SI
        return factory
            .createGraphQLSDLNodeForSourceIndex(sourceContainerTypeAsExpectedSourceIndexType)
            .filterInstanceOf()
    }

    private fun deriveObjectTypeDefinitionFromFirstAvailableGraphQLApiSourceContainerType(
        compositeContainerType: CompositeContainerType
    ): ObjectTypeDefinition {
        val firstGraphQLApiSourceContainerType: SourceContainerType<*> =
            (compositeContainerType
                .getSourceContainerTypeByDataSource()
                .asSequence()
                .filter { entry -> entry.key.sourceType == RawDataSourceType.GRAPHQL_API }
                .map { entry -> entry.value }
                .firstOrNull()
                ?: throw MaterializerException(
                    MaterializerErrorResponse.GRAPHQL_SCHEMA_CREATION_ERROR,
                    "graphql_api_source_container_type reported available but not found"
                ))
        val graphQLFieldsContainerType: GraphQLFieldsContainer =
            when (val graphQLSourceContainerType: SourceContainerType<*> =
                    firstGraphQLApiSourceContainerType
            ) {
                is GraphQLSourceContainerType -> {
                    graphQLSourceContainerType.containerType
                }
                else -> {
                    throw MaterializerException(
                        MaterializerErrorResponse.GRAPHQL_SCHEMA_CREATION_ERROR,
                        """graphql_source_container_type.type [ 
                                |expected: ${GraphQLSourceContainerType::class.qualifiedName}, 
                                |actual: ${firstGraphQLApiSourceContainerType::class.qualifiedName} 
                                |]""".flattenIntoOneLine()
                    )
                }
            }
        return when (val fieldsContainerDefinition: Node<Node<*>>? =
                graphQLFieldsContainerType.definition
        ) { //
            // null if fieldsContainerType was not based on an SDL definition
            null -> {
                if (graphQLFieldsContainerType.description?.isNotEmpty() == true) {
                    ObjectTypeDefinition.newObjectTypeDefinition()
                        .name(graphQLFieldsContainerType.name)
                        .description(
                            Description(
                                graphQLFieldsContainerType.description ?: "",
                                SourceLocation.EMPTY,
                                graphQLFieldsContainerType.description?.contains('\n') ?: false
                            )
                        )
                        /* Let attribute vertices be added separately
                         * .fieldDefinitions(
                         *    graphQLFieldsContainerType
                         *        .fieldDefinitions
                         *        .stream()
                         *        .map { fd -> fd.definition.toOption() }
                         *        .flatMapOptions()
                         *        .reduceToPersistentList()
                         *)
                         */
                        .build()
                } else {
                    ObjectTypeDefinition.newObjectTypeDefinition()
                        .name(graphQLFieldsContainerType.name)
                        /* Let attribute vertices be added separately
                         * .fieldDefinitions(
                         *     graphQLFieldsContainerType
                         *         .fieldDefinitions
                         *         .stream()
                         *         .map { fd -> fd.definition.toOption() }
                         *         .flatMapOptions()
                         *         .reduceToPersistentList()
                         * )
                         */
                        .build()
                }
            } //
            // only object_type_definition if fieldsContainerType is actually a GraphQLObjectType
            is ObjectTypeDefinition -> {
                fieldsContainerDefinition.transform { builder: ObjectTypeDefinition.Builder ->
                    /*
                     * Clear list of field_definitions already available in given object_type_definition
                     * Let this list be updated as source_attributes are updated
                     */
                    builder.fieldDefinitions(listOf())
                }
            }
            else -> {
                throw MaterializerException(
                    MaterializerErrorResponse.GRAPHQL_SCHEMA_CREATION_ERROR,
                    """graphql_fields_container_type does not conform to expected 
                      |contract for get_definition: [ graphql_field_container_type.definition.type: 
                      |${graphQLFieldsContainerType.definition::class.qualifiedName} 
                      |]""".flattenIntoOneLine()
                )
            }
        }
    }
}

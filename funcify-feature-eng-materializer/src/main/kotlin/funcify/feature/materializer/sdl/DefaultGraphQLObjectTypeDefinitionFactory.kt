package funcify.feature.materializer.sdl

import arrow.core.Option
import arrow.core.toOption
import funcify.feature.datasource.sdl.SourceIndexGqlSdlDefinitionFactory
import funcify.feature.materializer.error.MaterializerErrorResponse
import funcify.feature.materializer.error.MaterializerException
import funcify.feature.schema.datasource.DataSourceType
import funcify.feature.schema.datasource.SourceContainerType
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.schema.index.CompositeSourceContainerType
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.container.attempt.Try.Companion.filterInstanceOf
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import graphql.language.ObjectTypeDefinition
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
        sourceIndexGqlSdlDefinitionFactories.fold(persistentSetOf()) { ps, factory ->
            ps.add(factory.dataSourceType)
        }
    }

    private val sourceIndexGqlSdlDefFactoryBySourceIndexType:
        ImmutableMap<KClass<out SourceIndex<*>>, SourceIndexGqlSdlDefinitionFactory<*>> by lazy {
        sourceIndexGqlSdlDefinitionFactories.fold(persistentMapOf()) { pm, factory ->
            pm.put(factory.sourceIndexType, factory)
        }
    }

    private val cachingCompositeContainerTypeTypeToFactoryFunction:
        (CompositeSourceContainerType) -> Option<
                Pair<SourceContainerType<*, *>, SourceIndexGqlSdlDefinitionFactory<*>>> =
        createCachingFactoryForCompositeContainerTypeFunction()

    override fun createObjectTypeDefinitionForCompositeContainerType(
        compositeContainerType: CompositeSourceContainerType
    ): ObjectTypeDefinition {
        logger.debug(
            """create_object_type_definition_for_composite_container_type: 
               |[ composite_container_type.name: ${compositeContainerType.conventionalName} 
               |]""".flattenIntoOneLine()
        )
        return when {
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
        (CompositeSourceContainerType) -> Option<
                Pair<SourceContainerType<*, *>, SourceIndexGqlSdlDefinitionFactory<*>>> {
        val sourceIndexSuperTypeBySourceIndexSubType: MutableMap<KClass<*>, KClass<*>> =
            mutableMapOf()
        return { cct: CompositeSourceContainerType ->
            cct.getSourceContainerTypeByDataSource()
                .values
                .asSequence()
                .filter { sct: SourceContainerType<*, *> ->
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
                .flatMap { sct: SourceContainerType<*, *> ->
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

    private fun <SI : SourceIndex<SI>> createObjectTypeDefinitionUsingFactory(
        factory: SourceIndexGqlSdlDefinitionFactory<SI>,
        sourceContainerType: SourceContainerType<*, *>
    ): Try<ObjectTypeDefinition> {
        @Suppress("UNCHECKED_CAST") //
        val sourceContainerTypeAsExpectedSourceIndexType: SI = sourceContainerType as SI
        return factory
            .createGraphQLSDLNodeForSourceIndex(sourceContainerTypeAsExpectedSourceIndexType)
            .filterInstanceOf()
    }
}

package funcify.feature.materializer.sdl

import arrow.core.Option
import arrow.core.toOption
import funcify.feature.datasource.sdl.SourceIndexGqlSdlDefinitionFactory
import funcify.feature.materializer.error.MaterializerErrorResponse
import funcify.feature.materializer.error.MaterializerException
import funcify.feature.schema.datasource.DataSourceType
import funcify.feature.schema.datasource.RawDataSourceType
import funcify.feature.schema.datasource.SourceAttribute
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.schema.index.CompositeSourceAttribute
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.container.attempt.Try.Companion.filterInstanceOf
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import graphql.language.FieldDefinition
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
                (CompositeSourceAttribute) -> Option<
                Pair<SourceAttribute, SourceIndexGqlSdlDefinitionFactory<*>>> =
        createCachingFactoryForCompositeAttributeFunction()

    override fun createFieldDefinitionForCompositeAttribute(
        compositeAttribute: CompositeSourceAttribute
    ): FieldDefinition {
        logger.debug(
            """create_field_definition_for_composite_attribute: [ 
                |composite_attribute.name: ${compositeAttribute.conventionalName} 
                |]""".flattenIntoOneLine()
        )
        return when {
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
                (CompositeSourceAttribute) -> Option<
                Pair<SourceAttribute, SourceIndexGqlSdlDefinitionFactory<*>>> {
        val sourceIndexSuperTypeBySourceIndexSubType: MutableMap<KClass<*>, KClass<*>> =
            mutableMapOf()
        return { ca: CompositeSourceAttribute ->
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
}

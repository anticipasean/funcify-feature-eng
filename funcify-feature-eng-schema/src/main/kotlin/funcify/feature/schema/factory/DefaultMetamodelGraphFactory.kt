package funcify.feature.schema.factory

import funcify.feature.schema.MetamodelGraph
import funcify.feature.schema.SchematicEdge
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.datasource.DataElementSource
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.schema.directive.alias.AttributeAliasRegistry
import funcify.feature.schema.directive.alias.DataSourceAttributeAliasProvider
import funcify.feature.schema.directive.identifier.DataSourceEntityIdentifiersProvider
import funcify.feature.schema.directive.identifier.EntityRegistry
import funcify.feature.schema.directive.temporal.DataSourceAttributeLastUpdatedProvider
import funcify.feature.schema.directive.temporal.LastUpdatedTemporalAttributePathRegistry
import funcify.feature.schema.error.SchemaErrorResponse
import funcify.feature.schema.error.SchemaException
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.strategy.CompositeSchematicVertexGraphRemappingStrategy
import funcify.feature.schema.strategy.SchematicVertexGraphRemappingStrategy
import funcify.feature.tools.container.graph.PathBasedGraph
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.ThrowableExtensions.possiblyNestedHeadStackTraceElement
import kotlinx.collections.immutable.persistentMapOf
import org.slf4j.Logger
import reactor.core.publisher.Mono

internal class DefaultMetamodelGraphFactory(val schematicVertexFactory: SchematicVertexFactory) :
    MetamodelGraphFactory {

    companion object {

        private val logger: Logger = loggerFor<DefaultMetamodelGraphFactory>()

        internal data class DefaultBuilder(
            var deferredMetamodelGraphCreationContext: Mono<MetamodelGraphCreationContext>,
            val creationFactory:
                MetamodelGraphCreationStrategyTemplate<Mono<MetamodelGraphCreationContext>> =
                DefaultMetamodelGraphCreationStrategy()
        ) : MetamodelGraph.Builder {

            override fun <SI : SourceIndex<SI>> addDataSource(
                dataSource: DataElementSource<SI>
            ): MetamodelGraph.Builder {
                deferredMetamodelGraphCreationContext =
                    creationFactory.addDataSource(dataSource, deferredMetamodelGraphCreationContext)
                return this
            }

            override fun <SI : SourceIndex<SI>> addAttributeAliasProviderForDataSource(
                attributeAliasProvider: DataSourceAttributeAliasProvider<SI>,
                dataSource: DataElementSource<SI>,
            ): MetamodelGraph.Builder {
                deferredMetamodelGraphCreationContext =
                    creationFactory.addAttributeAliasProviderForDataSource(
                        attributeAliasProvider,
                        dataSource,
                        deferredMetamodelGraphCreationContext
                    )
                return this
            }

            override fun <SI : SourceIndex<SI>> addLastUpdatedAttributeProviderForDataSource(
                lastUpdatedAttributeProvider: DataSourceAttributeLastUpdatedProvider<SI>,
                dataSource: DataElementSource<SI>,
            ): MetamodelGraph.Builder {
                deferredMetamodelGraphCreationContext =
                    creationFactory.addLastUpdatedAttributeProviderForDataSource(
                        lastUpdatedAttributeProvider,
                        dataSource,
                        deferredMetamodelGraphCreationContext
                    )
                return this
            }

            override fun <SI : SourceIndex<SI>> addEntityIdentifiersProviderForDataSource(
                entityIdentifiersProvider: DataSourceEntityIdentifiersProvider<SI>,
                dataSource: DataElementSource<SI>,
            ): MetamodelGraph.Builder {
                deferredMetamodelGraphCreationContext =
                    creationFactory.addEntityIdentifiersProviderForDataSource(
                        entityIdentifiersProvider,
                        dataSource,
                        deferredMetamodelGraphCreationContext
                    )
                return this
            }

            override fun addRemappingStrategyForPostProcessingSchematicVertices(
                strategy: SchematicVertexGraphRemappingStrategy<MetamodelGraphCreationContext>
            ): MetamodelGraph.Builder {
                deferredMetamodelGraphCreationContext =
                    creationFactory.addSchematicVertexGraphRemappingStrategy(
                        strategy,
                        deferredMetamodelGraphCreationContext
                    )
                return this
            }

            override fun build(): Mono<MetamodelGraph> {
                return deferredMetamodelGraphCreationContext
                    .flatMap { context: MetamodelGraphCreationContext ->
                        creationFactory.applySchematicVertexGraphRemappingStrategy(
                            context.schematicVertexGraphRemappingStrategy,
                            Mono.just(context)
                        )
                    }
                    .flatMap { context: MetamodelGraphCreationContext ->
                        if (context.errors.isNotEmpty()) {
                            val errorsListAsStr =
                                context.errors.joinToString(
                                    separator = ",\n  ",
                                    prefix = "\n{ ",
                                    postfix = " }",
                                    transform = { thr ->
                                        """[ type: ${thr::class.simpleName}, 
                                           |message: ${thr.message}, 
                                           |stacktrace[0]: "${thr.possiblyNestedHeadStackTraceElement()}" 
                                           |]""".flatten()
                                    }
                                )
                            Mono.error(
                                SchemaException(
                                    SchemaErrorResponse.METAMODEL_CREATION_ERROR,
                                    "one or more errors occurred during metamodel_graph creation: $errorsListAsStr"
                                )
                            )
                        } else {
                            Mono.just(
                                DefaultMetamodelGraph(
                                    context.dataSourcesByName.values.fold(persistentMapOf()) {
                                        dsMap,
                                        ds ->
                                        dsMap.put(ds.key, ds)
                                    },
                                    PathBasedGraph.emptyTwoToOnePathsToEdgeGraph<
                                            SchematicPath, SchematicVertex, SchematicEdge>()
                                        .putAllVertices(context.schematicVerticesByPath),
                                    context.aliasRegistry,
                                    context.lastUpdatedTemporalAttributePathRegistry,
                                    context.entityRegistry
                                )
                            )
                        }
                    }
            }
        }
    }

    override fun builder(): MetamodelGraph.Builder {
        return DefaultBuilder(
            Mono.just(
                DefaultMetamodelGraphCreationContext(
                    schematicVertexFactory = schematicVertexFactory,
                    schematicVertexGraphRemappingStrategy =
                        CompositeSchematicVertexGraphRemappingStrategy(),
                    aliasRegistry = AttributeAliasRegistry.newRegistry(),
                    lastUpdatedTemporalAttributePathRegistry =
                        LastUpdatedTemporalAttributePathRegistry.newRegistry(),
                    entityRegistry = EntityRegistry.newRegistry()
                )
            )
        )
    }
}

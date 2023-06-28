package funcify.feature.schema.factory

import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.datasource.DataElementSource
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.schema.directive.alias.DataSourceAttributeAliasProvider
import funcify.feature.schema.directive.identifier.DataSourceEntityIdentifiersProvider
import funcify.feature.schema.directive.temporal.DataSourceAttributeLastUpdatedProvider
import funcify.feature.schema.error.SchemaErrorResponse
import funcify.feature.schema.error.SchemaException
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.strategy.CompositeSchematicVertexGraphRemappingStrategy
import funcify.feature.schema.strategy.SchematicVertexGraphRemappingStrategy
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.MonoExtensions.widen
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.ThrowableExtensions.possiblyNestedHeadStackTraceElement
import funcify.feature.tools.extensions.TryExtensions.successIfNonNull
import kotlinx.collections.immutable.persistentListOf
import org.slf4j.Logger
import reactor.core.publisher.Mono

/**
 *
 * @author smccarron
 * @created 2022-07-25
 */
internal class DefaultMetamodelGraphCreationStrategy() :
    MetamodelGraphCreationStrategyTemplate<Mono<MetamodelGraphCreationContext>> {

    companion object {
        private val logger: Logger = loggerFor<DefaultMetamodelGraphCreationStrategy>()
    }

    override fun <SI : SourceIndex<SI>> addDataSource(
        dataSource: DataElementSource<SI>,
        contextContainer: Mono<MetamodelGraphCreationContext>,
    ): Mono<MetamodelGraphCreationContext> {
        val methodTag: String = "add_data_source"
        logger.debug("${methodTag}: [ datasource.name: ${dataSource.name} ]")
        return contextContainer.flatMap { context ->
            if (context.dataSourcesByName.containsKey(dataSource.name)) {
                val message =
                    """data_source already added by same name: 
                       |[ name: ${dataSource.name} ]
                       |""".flatten()
                Mono.just(
                    context.update {
                        addError(
                            SchemaException(
                                SchemaErrorResponse.UNIQUE_CONSTRAINT_VIOLATION,
                                message
                            )
                        )
                    }
                )
            } else {
                dataSource.sourceMetamodel.sourceIndicesByPath
                    .asSequence()
                    .flatMap { (path, siSet) -> siSet.asSequence().map { si -> path to si } }
                    .sortedWith(
                        Comparator.comparing(
                            Pair<SchematicPath, SI>::first,
                            SchematicPath.comparator()
                        )
                    )
                    .fold(Mono.just(context.update { addDataSource(dataSource) })) {
                        ctxDef,
                        (path, sourceIndex) ->
                        createNewOrUpdateExistingSchematicVertex(
                            dataSource,
                            path,
                            sourceIndex,
                            ctxDef
                        )
                    }
                    .flatMap { updatedContext ->
                        if (updatedContext.errors.isNotEmpty()) {
                            val errorsListAsStr =
                                updatedContext.errors.joinToString(
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
                                    """one or more errors occurred during metamodel_graph creation 
                                        |when adding data_source [ name: ${dataSource.name} ]: 
                                        |$errorsListAsStr""".flatten()
                                )
                            )
                        } else {
                            Mono.just(updatedContext)
                        }
                    }
            }
        }
    }
    override fun <SI : SourceIndex<SI>> addAttributeAliasProviderForDataSource(
        attributeAliasProvider: DataSourceAttributeAliasProvider<SI>,
        dataSource: DataElementSource<SI>,
        contextContainer: Mono<MetamodelGraphCreationContext>,
    ): Mono<MetamodelGraphCreationContext> {
        val methodTag: String = "add_attribute_alias_provider_for_data_source"
        logger.debug(
            "${methodTag}: [ attribute_alias_provider.type: ${attributeAliasProvider::class.simpleName}, datasource.name: ${dataSource.name} ]"
        )
        return contextContainer
            .flatMap { context ->
                if (context.dataSourcesByName.containsKey(dataSource.name)) {
                    contextContainer
                } else {
                    addDataSource(dataSource, contextContainer)
                }
            }
            .flatMap { context ->
                fetchAliasesForDataSourceFromProvider(
                    dataSource,
                    attributeAliasProvider,
                    Mono.just(context)
                )
            }
    }

    override fun <SI : SourceIndex<SI>> addLastUpdatedAttributeProviderForDataSource(
        lastUpdatedAttributeProvider: DataSourceAttributeLastUpdatedProvider<SI>,
        dataSource: DataElementSource<SI>,
        contextContainer: Mono<MetamodelGraphCreationContext>,
    ): Mono<MetamodelGraphCreationContext> {
        val methodTag: String = "add_last_updated_attribute_provider_for_data_source"
        logger.debug(
            "${methodTag}: [ datasource.name: ${dataSource.name}, last_updated_attribute_provider.type: ${lastUpdatedAttributeProvider::class.qualifiedName} ]"
        )
        return contextContainer
            .flatMap { context ->
                if (context.dataSourcesByName.containsKey(dataSource.name)) {
                    contextContainer
                } else {
                    addDataSource(dataSource, contextContainer)
                }
            }
            .flatMap { context ->
                fetchLastUpdatedTemporalAttributesForDataSourceFromProvider(
                    dataSource,
                    lastUpdatedAttributeProvider,
                    Mono.just(context)
                )
            }
    }

    override fun <SI : SourceIndex<SI>> addEntityIdentifiersProviderForDataSource(
        entityIdentifiersProvider: DataSourceEntityIdentifiersProvider<SI>,
        dataSource: DataElementSource<SI>,
        contextContainer: Mono<MetamodelGraphCreationContext>,
    ): Mono<MetamodelGraphCreationContext> {
        val methodTag: String = "add_entity_identifiers_provider_for_data_source"
        logger.debug(
            "${methodTag}: [ datasource.name: ${dataSource.name}, entity_identifiers_provider.type: ${entityIdentifiersProvider::class.qualifiedName} ]"
        )
        return contextContainer
            .flatMap { context ->
                if (context.dataSourcesByName.containsKey(dataSource.name)) {
                    contextContainer
                } else {
                    addDataSource(dataSource, contextContainer)
                }
            }
            .flatMap { context ->
                fetchEntityIdentifierAttributesForDataSourceFromProvider(
                    dataSource,
                    entityIdentifiersProvider,
                    Mono.just(context)
                )
            }
    }

    override fun <SI : SourceIndex<SI>> createNewOrUpdateExistingSchematicVertex(
        dataSource: DataElementSource<SI>,
        sourcePath: SchematicPath,
        sourceIndex: SI,
        contextContainer: Mono<MetamodelGraphCreationContext>,
    ): Mono<MetamodelGraphCreationContext> {
        val methodTag: String = "create_new_or_update_existing_schematic_vertex"
        logger.debug(
            "${methodTag}: [ source_path: ${sourcePath}, source_index: ${sourceIndex.name} ]"
        )
        return contextContainer.map { context ->
            when (
                val existingVertex: SchematicVertex? = context.schematicVerticesByPath[sourcePath]
            ) {
                null -> {
                    context.schematicVertexFactory
                        .createVertexForPath(sourcePath)
                        .extractingName()
                        .forSourceIndex<SI>(sourceIndex)
                }
                else -> {
                    context.schematicVertexFactory
                        .createVertexForPath(sourcePath)
                        .extractingName()
                        .fromExistingVertex(existingVertex)
                        .forSourceIndex<SI>(sourceIndex)
                }
            }.fold(
                { v -> context.update { addOrUpdateSchematicVertexAtPath(sourcePath, v) } },
                { t -> context.update { addError(t) } }
            )
        }
    }

    override fun <SI : SourceIndex<SI>> fetchAliasesForDataSourceFromProvider(
        dataSource: DataElementSource<SI>,
        aliasProvider: DataSourceAttributeAliasProvider<SI>,
        contextContainer: Mono<MetamodelGraphCreationContext>,
    ): Mono<MetamodelGraphCreationContext> {
        val methodTag: String = "fetch_aliases_for_data_source_from_provider"
        logger.debug(
            "${methodTag}: [ datasource.name: ${dataSource.name}, alias_provider.type: ${aliasProvider::class.qualifiedName} ]"
        )
        return contextContainer.flatMap { context ->
            aliasProvider
                .provideAnyAliasesForAttributePathsInDataSource(dataSource)
                .cache()
                .map { aliasSetBySchematicPath ->
                    aliasSetBySchematicPath.asSequence().fold(context.aliasRegistry) {
                        areg,
                        (path, aliasSet) ->
                        when {
                            path.arguments.isNotEmpty() ->
                                aliasSet.fold(areg) { ar, n ->
                                    ar.registerParameterVertexPathWithAlias(path, n)
                                }
                            else ->
                                aliasSet.fold(areg) { ar, n ->
                                    ar.registerSourceVertexPathWithAlias(path, n)
                                }
                        }
                    }
                }
                .map { updatedRegistry -> context.update { aliasRegistry(updatedRegistry) } }
                .onErrorResume { throwable: Throwable ->
                    Mono.just(context.update { addError(throwable) })
                }
        }
    }

    override fun <SI : SourceIndex<SI>> fetchLastUpdatedTemporalAttributesForDataSourceFromProvider(
        dataSource: DataElementSource<SI>,
        lastUpdatedProvider: DataSourceAttributeLastUpdatedProvider<SI>,
        contextContainer: Mono<MetamodelGraphCreationContext>,
    ): Mono<MetamodelGraphCreationContext> {
        val methodTag: String =
            "fetch_last_updated_temporal_attributes_for_data_source_from_provider"
        logger.debug(
            "${methodTag}: [ datasource.name: ${dataSource.name}, last_updated_provider.type: ${lastUpdatedProvider::class.qualifiedName} ]"
        )
        return contextContainer.flatMap { context ->
            lastUpdatedProvider
                .provideTemporalAttributePathsInDataSourceForUseInLastUpdatedCalculations(
                    dataSource
                )
                .cache()
                .map { pathsForTemporalAttrs ->
                    pathsForTemporalAttrs.fold(context.lastUpdatedTemporalAttributePathRegistry) {
                        reg,
                        path ->
                        reg.registerSchematicPathAsMappingToLastUpdatedTemporalAttributeVertex(path)
                    }
                }
                .map { lastUpdReg ->
                    context.update { lastUpdatedTemporalAttributePathRegistry(lastUpdReg) }
                }
                .onErrorResume { thr -> Mono.just(context.update { addError(thr) }) }
        }
    }

    override fun <SI : SourceIndex<SI>> fetchEntityIdentifierAttributesForDataSourceFromProvider(
        dataSource: DataElementSource<SI>,
        entityIdentifiersProvider: DataSourceEntityIdentifiersProvider<SI>,
        contextContainer: Mono<MetamodelGraphCreationContext>,
    ): Mono<MetamodelGraphCreationContext> {
        val methodTag: String = "fetch_entity_identifier_attributes_for_data_source_from_provider"
        logger.debug(
            "${methodTag}: [ datasource.name: ${dataSource.name}, entity_identifiers_provider.type: ${entityIdentifiersProvider::class.qualifiedName} ]"
        )
        return contextContainer.flatMap { context ->
            entityIdentifiersProvider
                .provideEntityIdentifierSourceAttributePathsInDataSource(dataSource)
                .cache()
                .map { pathsForEntityIdAttrs ->
                    pathsForEntityIdAttrs.fold(context.entityRegistry) { reg, path ->
                        reg.registerSchematicPathAsMappingToEntityIdentifierAttributeVertex(path)
                    }
                }
                .map { entityReg -> context.update { entityRegistry(entityReg) } }
                .onErrorResume { thr -> Mono.just(context.update { addError(thr) }) }
        }
    }

    override fun addSchematicVertexGraphRemappingStrategy(
        strategy: SchematicVertexGraphRemappingStrategy<MetamodelGraphCreationContext>,
        contextContainer: Mono<MetamodelGraphCreationContext>,
    ): Mono<MetamodelGraphCreationContext> {
        val methodTag: String = "add_schematic_vertex_graph_remapping_strategy"
        logger.debug("${methodTag}: [ strategy.type: ${strategy::class.qualifiedName} ]")
        return contextContainer.map { context ->
            val updatedStrategy =
                when (val currentStrategy = context.schematicVertexGraphRemappingStrategy) {
                    is CompositeSchematicVertexGraphRemappingStrategy ->
                        currentStrategy.addStrategy(strategy)
                    else ->
                        CompositeSchematicVertexGraphRemappingStrategy(
                            persistentListOf(currentStrategy, strategy)
                        )
                }
            context.update { schematicVertexGraphRemappingStrategy(updatedStrategy) }
        }
    }

    override fun applySchematicVertexGraphRemappingStrategy(
        schematicVertexGraphRemappingStrategy:
            SchematicVertexGraphRemappingStrategy<MetamodelGraphCreationContext>,
        contextContainer: Mono<MetamodelGraphCreationContext>,
    ): Mono<MetamodelGraphCreationContext> {
        val methodTag: String = "apply_schematic_vertex_graph_remapping_strategy"
        logger.debug(
            """${methodTag}: [ schematic_vertex_graph_remapping_strategy.type: 
               |${schematicVertexGraphRemappingStrategy::class.qualifiedName} 
               |]""".flatten()
        )
        return contextContainer.flatMap { context ->
            val remappingStrategy = context.schematicVertexGraphRemappingStrategy
            context.schematicVerticesByPath.keys
                .fold(Try.success(context)) { ctxAttempt, path ->
                    ctxAttempt.flatMap { ctx ->
                        if (path in ctx.schematicVerticesByPath) {
                            val vertex = ctx.schematicVerticesByPath[path]!!
                            if (remappingStrategy.canBeAppliedTo(ctx, vertex)) {
                                remappingStrategy.applyToVertexInContext(ctx, vertex)
                            } else {
                                ctx.successIfNonNull()
                            }
                        } else {
                            ctx.successIfNonNull()
                        }
                    }
                }
                .toMono()
                .widen()
                .onErrorResume { t: Throwable -> Mono.just(context.update { addError(t) }) }
        }
    }
}

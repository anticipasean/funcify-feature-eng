package funcify.feature.schema.factory

import arrow.core.Either
import arrow.core.left
import arrow.core.none
import arrow.core.right
import arrow.core.some
import arrow.core.toOption
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.schema.directive.alias.DataSourceAttributeAliasProvider
import funcify.feature.schema.directive.temporal.DataSourceAttributeLastUpdatedProvider
import funcify.feature.schema.error.SchemaErrorResponse
import funcify.feature.schema.error.SchemaException
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.strategy.SchematicVertexGraphRemappingStrategy
import funcify.feature.schema.vertex.ParameterAttributeVertex
import funcify.feature.schema.vertex.SourceAttributeVertex
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.container.deferred.Deferred
import funcify.feature.tools.extensions.DeferredExtensions.deferred
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import funcify.feature.tools.extensions.TryExtensions.successIfNonNull
import org.slf4j.Logger

/**
 *
 * @author smccarron
 * @created 2022-07-25
 */
internal class DefaultMetamodelGraphCreationStrategy :
    MetamodelGraphCreationStrategyTemplate<Deferred<MetamodelGraphCreationContext>> {

    companion object {
        private val logger: Logger = loggerFor<DefaultMetamodelGraphCreationStrategy>()
    }

    override fun <SI : SourceIndex<SI>> addDataSource(
        dataSource: DataSource<SI>,
        contextContainer: Deferred<MetamodelGraphCreationContext>,
    ): Deferred<MetamodelGraphCreationContext> {
        val methodTag: String = "add_data_source"
        logger.debug("${methodTag}: [ datasource.name: ${dataSource.name} ]")
        return contextContainer.flatMap { context ->
            if (context.dataSourcesByName.containsKey(dataSource.name)) {
                val message =
                    """data_source already added by same name: 
                       |[ name: ${dataSource.name} ]
                       |""".flattenIntoOneLine()
                Deferred.completed(
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
                    .fold(Deferred.completed(context.update { addDataSource(dataSource) })) {
                        ctxDef,
                        (path, sourceIndex) ->
                        createNewOrUpdateExistingSchematicVertex(
                            dataSource,
                            path,
                            sourceIndex,
                            ctxDef
                        )
                    }
            }
        }
    }
    override fun <SI : SourceIndex<SI>> addAttributeAliasProviderForDataSource(
        attributeAliasProvider: DataSourceAttributeAliasProvider<SI>,
        dataSource: DataSource<SI>,
        contextContainer: Deferred<MetamodelGraphCreationContext>,
    ): Deferred<MetamodelGraphCreationContext> {
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
                    Deferred.completed(context)
                )
            }
    }

    override fun <SI : SourceIndex<SI>> addLastUpdatedAttributeProviderForDataSource(
        lastUpdatedAttributeProvider: DataSourceAttributeLastUpdatedProvider<SI>,
        dataSource: DataSource<SI>,
        contextContainer: Deferred<MetamodelGraphCreationContext>,
    ): Deferred<MetamodelGraphCreationContext> {
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
                    Deferred.completed(context)
                )
            }
    }

    override fun <SI : SourceIndex<SI>> createNewOrUpdateExistingSchematicVertex(
        dataSource: DataSource<SI>,
        sourcePath: SchematicPath,
        sourceIndex: SI,
        contextContainer: Deferred<MetamodelGraphCreationContext>,
    ): Deferred<MetamodelGraphCreationContext> {
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
                        .forSourceIndex<SI>(sourceIndex)
                        .onDataSource(dataSource.key)
                }
                else -> {

                    context.schematicVertexFactory
                        .createVertexForPath(sourcePath)
                        .fromExistingVertex(existingVertex)
                        .forSourceIndex<SI>(sourceIndex)
                        .onDataSource(dataSource.key)
                }
            }.fold(
                { v -> context.update { addOrUpdateSchematicVertexAtPath(sourcePath, v) } },
                { t -> context.update { addError(t) } }
            )
        }
    }

    override fun <SI : SourceIndex<SI>> fetchAliasesForDataSourceFromProvider(
        dataSource: DataSource<SI>,
        aliasProvider: DataSourceAttributeAliasProvider<SI>,
        contextContainer: Deferred<MetamodelGraphCreationContext>,
    ): Deferred<MetamodelGraphCreationContext> {
        val methodTag: String = "fetch_aliases_for_data_source_from_provider"
        logger.debug(
            "${methodTag}: [ datasource.name: ${dataSource.name}, alias_provider.type: ${aliasProvider::class.qualifiedName} ]"
        )
        return contextContainer.flatMap { context ->
            aliasProvider
                .provideAnyAliasesForAttributePathsInDataSource(dataSource)
                .map { aliasSetBySchematicPath ->
                    aliasSetBySchematicPath.asSequence().fold(context.aliasRegistry) {
                        areg,
                        (path, aliasSet) ->
                        when (
                            val srcOrParamAttr =
                                context.schematicVerticesByPath[path]
                                    .toOption()
                                    .flatMap { v ->
                                        when (v) {
                                            is SourceAttributeVertex -> v.left().some()
                                            is ParameterAttributeVertex -> v.right().some()
                                            else -> none()
                                        }
                                    }
                                    .orNull()
                        ) {
                            is Either.Left ->
                                aliasSet.fold(areg) { reg, alias ->
                                    reg.registerSourceAttributeVertexWithAlias(
                                        srcOrParamAttr.value,
                                        alias
                                    )
                                }
                            is Either.Right ->
                                aliasSet.fold(areg) { reg, alias ->
                                    reg.registerParameterAttributeVertexWithAlias(
                                        srcOrParamAttr.value,
                                        alias
                                    )
                                }
                            else -> areg
                        }
                    }
                }
                .map { updatedRegistry -> context.update { aliasRegistry(updatedRegistry) } }
                .flatMapFailure { throwable: Throwable ->
                    Deferred.completed(context.update { addError(throwable) })
                }
        }
    }

    override fun <SI : SourceIndex<SI>> fetchLastUpdatedTemporalAttributesForDataSourceFromProvider(
        dataSource: DataSource<SI>,
        lastUpdatedProvider: DataSourceAttributeLastUpdatedProvider<SI>,
        contextContainer: Deferred<MetamodelGraphCreationContext>,
    ): Deferred<MetamodelGraphCreationContext> {
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
                .flatMapFailure { thr -> Deferred.completed(context.update { addError(thr) }) }
        }
    }

    override fun applySchematicVertexGraphRemappingStrategy(
        schematicVertexGraphRemappingStrategy:
            SchematicVertexGraphRemappingStrategy<MetamodelGraphCreationContext>,
        contextContainer: Deferred<MetamodelGraphCreationContext>,
    ): Deferred<MetamodelGraphCreationContext> {
        val methodTag: String = "apply_schematic_vertex_graph_remapping_strategy"
        logger.debug(
            """${methodTag}: [ schematic_vertex_graph_remapping_strategy.type: 
               |${schematicVertexGraphRemappingStrategy::class.qualifiedName} 
               |]""".flattenIntoOneLine()
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
                .deferred()
                .flatMapFailure { t: Throwable ->
                    Deferred.completed(context.update { addError(t) })
                }
        }
    }
}

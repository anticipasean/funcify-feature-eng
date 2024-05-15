package funcify.feature.materializer.graph.connector

import arrow.core.getOrElse
import arrow.core.getOrNone
import arrow.core.orElse
import arrow.core.toOption
import funcify.feature.error.ServiceError
import funcify.feature.materializer.graph.context.RequestMaterializationGraphContext
import funcify.feature.materializer.graph.context.StandardQuery
import funcify.feature.materializer.graph.context.TabularQuery
import funcify.feature.materializer.model.MaterializationMetamodel
import funcify.feature.schema.dataelement.DomainSpecifiedDataElementSource
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.OptionExtensions.sequence
import funcify.feature.tools.extensions.StringExtensions.toCamelCase
import graphql.schema.FieldCoordinates
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentSet
import org.slf4j.Logger

internal object RequestMaterializationGraphContextPreconnector :
    (RequestMaterializationGraphContext) -> RequestMaterializationGraphContext {

    private const val METHOD_TAG: String =
        "request_materialization_graph_context_preconnector.invoke"
    private val logger: Logger = loggerFor<RequestMaterializationGraphContextPreconnector>()

    private val matchingVariableKeyArgumentPathsByDomainDataElementPathsCalculator:
        (MaterializationMetamodel, ImmutableSet<String>) -> PersistentMap<
                GQLOperationPath,
                PersistentMap<GQLOperationPath, String>
            > by lazy {
        val cache:
            ConcurrentMap<
                Pair<Instant, ImmutableSet<String>>,
                PersistentMap<GQLOperationPath, PersistentMap<GQLOperationPath, String>>
            > =
            ConcurrentHashMap();
        { mm: MaterializationMetamodel, vks: ImmutableSet<String> ->
            cache.computeIfAbsent(mm.created to vks) { (_: Instant, vars: ImmutableSet<String>) ->
                computeMatchingVariableKeyArgumentPathsByDomainDataElementPaths(mm, vars)
            }
        }
    }

    override fun invoke(
        connectorContext: RequestMaterializationGraphContext
    ): RequestMaterializationGraphContext {
        logger.info("{}: [ ]", METHOD_TAG)
        return when (connectorContext) {
            is StandardQuery -> {
                connectorContext.update {
                    setMatchingVariablesForArgumentsForDomainDataElementPath(
                        matchingVariableKeyArgumentPathsByDomainDataElementPathsCalculator(
                            connectorContext.materializationMetamodel,
                            connectorContext.variableKeys
                        )
                    )
                }
            }
            is TabularQuery -> {
                connectorContext.update {
                    setMatchingVariablesForArgumentsForDomainDataElementPath(
                        matchingVariableKeyArgumentPathsByDomainDataElementPathsCalculator(
                            connectorContext.materializationMetamodel,
                            connectorContext.variableKeys
                        )
                    )
                }
            }
            else -> {
                throw ServiceError.of(
                    "unsupported instance [ type: %s ] of [ type: %s ]",
                    connectorContext::class.qualifiedName,
                    RequestMaterializationGraphContext::class.qualifiedName
                )
            }
        }.also { c: RequestMaterializationGraphContext ->
            logger.debug(
                "{}: [ matching_argument_paths_to_variable_key_by_domain_data_element_paths: {} ]",
                METHOD_TAG,
                c.matchingArgumentPathsToVariableKeyByDomainDataElementPaths
                    .asSequence()
                    .joinToString(",\n", "{ ", " }") { (k, v) -> "${k}: [${v}]" }
            )
        }
    }

    private fun computeMatchingVariableKeyArgumentPathsByDomainDataElementPaths(
        materializationMetamodel: MaterializationMetamodel,
        variableKeys: ImmutableSet<String>
    ): PersistentMap<GQLOperationPath, PersistentMap<GQLOperationPath, String>> {
        return variableKeys
            .asSequence()
            .flatMap { vk: String ->
                materializationMetamodel.aliasCoordinatesRegistry
                    .getFieldArgumentsWithAlias(vk)
                    .toOption()
                    .filter(ImmutableSet<Pair<FieldCoordinates, String>>::isNotEmpty)
                    .map { argLocs: ImmutableSet<Pair<FieldCoordinates, String>> ->
                        argLocs
                            .asSequence()
                            .flatMap { argLoc: Pair<FieldCoordinates, String> ->
                                materializationMetamodel.dataElementPathByArgLocation
                                    .getOrNone(argLoc)
                                    .getOrElse(::persistentSetOf)
                                    .asSequence()
                            }
                            .toPersistentSet()
                    }
                    .filter(ImmutableSet<GQLOperationPath>::isNotEmpty)
                    .orElse {
                        materializationMetamodel.aliasCoordinatesRegistry
                            .getFieldArgumentsWithAlias(vk.toCamelCase())
                            .toOption()
                            .filter(ImmutableSet<Pair<FieldCoordinates, String>>::isNotEmpty)
                            .map { argLocs: ImmutableSet<Pair<FieldCoordinates, String>> ->
                                argLocs
                                    .asSequence()
                                    .flatMap { argLoc: Pair<FieldCoordinates, String> ->
                                        materializationMetamodel.dataElementPathByArgLocation
                                            .getOrNone(argLoc)
                                            .getOrElse(::persistentSetOf)
                                            .asSequence()
                                    }
                                    .toPersistentSet()
                            }
                            .filter(ImmutableSet<GQLOperationPath>::isNotEmpty)
                    }
                    .orElse {
                        materializationMetamodel.dataElementPathByFieldArgumentName
                            .getOrNone(vk)
                            .filter(ImmutableSet<GQLOperationPath>::isNotEmpty)
                    }
                    .orElse {
                        materializationMetamodel.dataElementPathByFieldArgumentName
                            .getOrNone(vk.toCamelCase())
                            .filter(ImmutableSet<GQLOperationPath>::isNotEmpty)
                    }
                    .map { aps: ImmutableSet<GQLOperationPath> -> vk to aps }
                    .sequence()
            }
            .flatMap { (vk: String, aps: ImmutableSet<GQLOperationPath>) ->
                aps.asSequence().flatMap { ap: GQLOperationPath ->
                    materializationMetamodel.domainSpecifiedDataElementSourceByPath
                        .asSequence()
                        .filter { (_: GQLOperationPath, dsdes: DomainSpecifiedDataElementSource) ->
                            dsdes.allArgumentsByPath.containsKey(ap)
                        }
                        .map { (dp: GQLOperationPath, _: DomainSpecifiedDataElementSource) ->
                            dp to (ap to vk)
                        }
                }
            }
            .fold(persistentMapOf<GQLOperationPath, PersistentMap<GQLOperationPath, String>>()) {
                pm: PersistentMap<GQLOperationPath, PersistentMap<GQLOperationPath, String>>,
                (dp: GQLOperationPath, argPathToVarKey: Pair<GQLOperationPath, String>) ->
                pm.put(
                    dp,
                    pm.getOrElse(dp, ::persistentMapOf)
                        .put(argPathToVarKey.first, argPathToVarKey.second)
                )
            }
    }
}

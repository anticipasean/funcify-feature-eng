package funcify.feature.materializer.model

import arrow.core.Option
import arrow.core.getOrElse
import arrow.core.getOrNone
import arrow.core.none
import arrow.core.toOption
import com.google.common.cache.CacheBuilder
import funcify.feature.schema.FeatureEngineeringModel
import funcify.feature.schema.dataelement.DomainSpecifiedDataElementSource
import funcify.feature.schema.directive.alias.AliasCoordinatesRegistry
import funcify.feature.schema.feature.FeatureSpecifiedFeatureCalculator
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.transformer.TransformerSpecifiedTransformerSource
import funcify.feature.tools.extensions.PairExtensions.fold
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentSetValueMap
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLSchemaElement
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

internal data class DefaultMaterializationMetamodel(
    override val created: Instant = Instant.now(),
    override val featureEngineeringModel: FeatureEngineeringModel,
    override val materializationGraphQLSchema: GraphQLSchema,
    override val elementTypeCoordinates: ImmutableSet<FieldCoordinates>,
    override val elementTypePaths: ImmutableSet<GQLOperationPath>,
    override val dataElementElementTypePath: GQLOperationPath,
    override val featureElementTypePath: GQLOperationPath,
    override val transformerElementTypePath: GQLOperationPath,
    override val aliasCoordinatesRegistry: AliasCoordinatesRegistry,
    override val childPathsByParentPath:
        ImmutableMap<GQLOperationPath, ImmutableSet<GQLOperationPath>>,
    override val querySchemaElementsByPath: ImmutableMap<GQLOperationPath, GraphQLSchemaElement>,
    override val fieldCoordinatesByPath:
        ImmutableMap<GQLOperationPath, ImmutableSet<FieldCoordinates>>,
    override val pathsByFieldCoordinates:
        ImmutableMap<FieldCoordinates, ImmutableSet<GQLOperationPath>>,
    override val domainSpecifiedDataElementSourceByPath:
        ImmutableMap<GQLOperationPath, DomainSpecifiedDataElementSource>,
    override val domainSpecifiedDataElementSourceByCoordinates:
        ImmutableMap<FieldCoordinates, DomainSpecifiedDataElementSource>,
    override val dataElementFieldCoordinatesByFieldName:
        ImmutableMap<String, ImmutableSet<FieldCoordinates>>,
    override val dataElementPathsByFieldName: ImmutableMap<String, ImmutableSet<GQLOperationPath>>,
    override val dataElementPathByFieldArgumentName:
        ImmutableMap<String, ImmutableSet<GQLOperationPath>>,
    override val featureSpecifiedFeatureCalculatorsByPath:
        ImmutableMap<GQLOperationPath, FeatureSpecifiedFeatureCalculator>,
    override val featureSpecifiedFeatureCalculatorsByCoordinates:
        ImmutableMap<FieldCoordinates, FeatureSpecifiedFeatureCalculator>,
    override val featurePathsByName: ImmutableMap<String, GQLOperationPath>,
    override val featureCoordinatesByName: ImmutableMap<String, FieldCoordinates>,
    override val transformerSpecifiedTransformerSourcesByPath:
        ImmutableMap<GQLOperationPath, TransformerSpecifiedTransformerSource>,
    override val transformerSpecifiedTransformerSourcesByCoordinates:
        ImmutableMap<FieldCoordinates, TransformerSpecifiedTransformerSource>,
) : MaterializationMetamodel {

    override val fieldCoordinatesAvailableUnderPath:
        (FieldCoordinates, GQLOperationPath) -> Boolean by lazy {
        val cache: ConcurrentMap<GQLOperationPath, Set<FieldCoordinates>> =
            CacheBuilder.newBuilder().build<GQLOperationPath, Set<FieldCoordinates>>().asMap()
        val fieldCoordinatesCalculator: (GQLOperationPath) -> Set<FieldCoordinates> =
            { parentPath: GQLOperationPath ->
                val fcSet: MutableSet<FieldCoordinates> = mutableSetOf()
                val deque: Deque<GQLOperationPath> =
                    LinkedList<GQLOperationPath>().apply { add(parentPath) }
                while (deque.isNotEmpty()) {
                    when (val p: GQLOperationPath = deque.pop()) {
                        in cache -> {
                            cache.getOrElse(p, ::setOf).forEach(fcSet::add)
                        }
                        else -> {
                            childPathsByParentPath
                                .getOrNone(p)
                                .fold(::emptySequence, ImmutableSet<GQLOperationPath>::asSequence)
                                .flatMap { cp: GQLOperationPath ->
                                    fieldCoordinatesByPath
                                        .getOrNone(cp)
                                        .fold(
                                            ::emptySequence,
                                            ImmutableSet<FieldCoordinates>::asSequence
                                        )
                                        .partition { fc: FieldCoordinates -> fc in fcSet }
                                        .fold {
                                            alreadyAddedCoordinates: List<FieldCoordinates>,
                                            newCoordinates: List<FieldCoordinates> ->
                                            when {
                                                alreadyAddedCoordinates.isEmpty() &&
                                                    newCoordinates.isNotEmpty() -> {
                                                    newCoordinates.forEach(fcSet::add)
                                                    sequenceOf(cp)
                                                }
                                                else -> {
                                                    newCoordinates.forEach(fcSet::add)
                                                    emptySequence<GQLOperationPath>()
                                                }
                                            }
                                        }
                                }
                                .forEach(deque::addLast)
                        }
                    }
                }
                fcSet.toSet()
            };
        { fc: FieldCoordinates, p: GQLOperationPath ->
            when (p) {
                !in fieldCoordinatesByPath -> {
                    false
                }
                !in childPathsByParentPath -> {
                    false
                }
                else -> {
                    cache.computeIfAbsent(p, fieldCoordinatesCalculator).contains(fc)
                }
            }
        }
    }

    override val firstPathWithFieldCoordinatesUnderPath:
        (FieldCoordinates, GQLOperationPath) -> Option<GQLOperationPath> by lazy {
        val cache: ConcurrentMap<Pair<FieldCoordinates, GQLOperationPath>, GQLOperationPath?> =
            CacheBuilder.newBuilder()
                .build<Pair<FieldCoordinates, GQLOperationPath>, GQLOperationPath?>()
                .asMap()
        val pathCalculator: (Pair<FieldCoordinates, GQLOperationPath>) -> GQLOperationPath? =
            { (fieldCoordinates: FieldCoordinates, parentPath: GQLOperationPath) ->
                pathsByFieldCoordinates
                    .getOrNone(fieldCoordinates)
                    .fold(::emptySequence, ImmutableSet<GQLOperationPath>::asSequence)
                    .filter { p: GQLOperationPath -> p.selection.size > parentPath.selection.size }
                    .sortedBy { p: GQLOperationPath -> p.selection.size }
                    .filter { p: GQLOperationPath -> parentPath.isAncestorTo(p) }
                    .firstOrNull()
            };
        { fc: FieldCoordinates, p: GQLOperationPath ->
            when {
                fc !in pathsByFieldCoordinates -> {
                    none()
                }
                p !in fieldCoordinatesByPath -> {
                    none()
                }
                else -> {
                    cache.computeIfAbsent(fc to p, pathCalculator).toOption()
                }
            }
        }
    }

    override val domainSpecifiedDataElementSourceArgumentPathsByArgLocation:
        ImmutableMap<Pair<FieldCoordinates, String>, GQLOperationPath> by lazy {
        domainSpecifiedDataElementSourceByCoordinates.values
            .asSequence()
            .flatMap { dsdes: DomainSpecifiedDataElementSource ->
                dsdes.domainArgumentsByPath.asSequence().map {
                    (p: GQLOperationPath, ga: GraphQLArgument) ->
                    (dsdes.domainFieldCoordinates to ga.name) to p
                }
            }
            .reducePairsToPersistentMap()
    }

    override val dataElementPathByArgLocation:
        ImmutableMap<Pair<FieldCoordinates, String>, ImmutableSet<GQLOperationPath>> by lazy {
        domainSpecifiedDataElementSourceByCoordinates.values
            .asSequence()
            .flatMap { dsdes: DomainSpecifiedDataElementSource ->
                dsdes.allArgumentsByPath.asSequence().flatMap {
                    (ap: GQLOperationPath, ga: GraphQLArgument) ->
                    ap.getParentPath()
                        .flatMap { pp: GQLOperationPath -> fieldCoordinatesByPath.getOrNone(pp) }
                        .getOrElse(::persistentSetOf)
                        .asSequence()
                        .map { fc: FieldCoordinates -> (fc to ga.name) to ap }
                }
            }
            .reducePairsToPersistentSetValueMap()
    }

    override val domainDataElementDataSourcePathByDescendentPath:
        (GQLOperationPath) -> Option<GQLOperationPath> by lazy {
        val cache: ConcurrentMap<GQLOperationPath, GQLOperationPath?> = ConcurrentHashMap()
        val domainDataElementPaths: ImmutableSet<GQLOperationPath> =
            domainSpecifiedDataElementSourceByPath.keys
        val domainDataElementPathSizes: Set<Int> =
            domainDataElementPaths
                .asSequence()
                .map { p: GQLOperationPath -> p.selection.size }
                .toSet()
        val calculation: (GQLOperationPath) -> GQLOperationPath? =
            if (domainDataElementPathSizes.isEmpty() || domainDataElementPathSizes.size > 1) {
                { p: GQLOperationPath ->
                    when {
                        !p.refersToSelection() -> {
                            null
                        }
                        else -> {
                            domainDataElementPaths.firstOrNull { dp: GQLOperationPath ->
                                dp.isAncestorTo(p)
                            }
                        }
                    }
                }
            } else {
                val commonDomainDataElementPathSize: Int = domainDataElementPathSizes.first();
                { p: GQLOperationPath ->
                    when {
                        !p.refersToSelection() -> {
                            null
                        }
                        p.selection.size < commonDomainDataElementPathSize -> {
                            null
                        }
                        p.selection.size == commonDomainDataElementPathSize &&
                            p in domainDataElementPaths -> {
                            p
                        }
                        p.selection.size > commonDomainDataElementPathSize -> {
                            val truncatedPath: GQLOperationPath =
                                GQLOperationPath.of {
                                    appendSelections(
                                        p.selection
                                            .asSequence()
                                            .take(commonDomainDataElementPathSize)
                                            .toList()
                                    )
                                }
                            if (truncatedPath in domainDataElementPaths) {
                                truncatedPath
                            } else {
                                null
                            }
                        }
                        else -> {
                            null
                        }
                    }
                }
            };
        { p: GQLOperationPath -> cache.computeIfAbsent(p, calculation).toOption() }
    }
}

package funcify.feature.schema.directive.identifier

import arrow.core.Option
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.none
import arrow.core.orElse
import arrow.core.right
import arrow.core.some
import arrow.core.toOption
import funcify.feature.schema.path.GQLOperationPath
import funcify.feature.tools.extensions.OptionExtensions.recurse
import funcify.feature.tools.extensions.SequenceExtensions.flatMapOptions
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

/**
 * @author smccarron
 * @created 2022-09-15
 */
internal class DefaultEntityRegistry(
    private val entityIdentifierAttributePathsSet: PersistentSet<GQLOperationPath> =
        persistentSetOf()
) : EntityRegistry {

    private val entityIdentifierAttributePathsByParentPath:
        PersistentMap<GQLOperationPath, PersistentSet<GQLOperationPath>> by lazy {
        entityIdentifierAttributePathsSet
            .asSequence()
            .map { sp -> sp.getParentPath().map { pp -> pp to sp } }
            .flatMapOptions()
            .fold(persistentMapOf<GQLOperationPath, PersistentSet<GQLOperationPath>>()) {
                pm,
                (pp, cp) ->
                pm.put(pp, pm.getOrElse(pp) { persistentSetOf() }.add(cp))
            }
    }

    private val nearestEntityIdentifierAttributeRelativesMemoizer:
        (GQLOperationPath) -> PersistentSet<GQLOperationPath> by lazy {
        val cache: ConcurrentMap<GQLOperationPath, PersistentSet<GQLOperationPath>> =
            ConcurrentHashMap()
        ({ path: GQLOperationPath ->
            cache
                .computeIfAbsent(path, nearestEntityIdentifierAttributeRelativesCalculator())
                .toOption()
                .getOrElse { persistentSetOf() }
        })
    }

    private val nearestEntitySourceContainerTypeVertexAncestorMemoizer:
        (GQLOperationPath) -> Option<GQLOperationPath> by lazy {
        val cache: ConcurrentMap<GQLOperationPath, GQLOperationPath> = ConcurrentHashMap()
        ({ path: GQLOperationPath ->
            cache
                .computeIfAbsent(path, nearestEntitySourceContainerTypeVertexAncestorCalculator())
                .toOption()
        })
    }

    override fun registerSchematicPathAsMappingToEntityIdentifierAttributeVertex(
        path: GQLOperationPath
    ): EntityRegistry {
        return when {
            // Path cannot represent a parameter vertex, as even parameter containers are not
            // required in any sense to have unique identifier attributes
            path.argument.isNotEmpty() || path.directive.isNotEmpty() -> {
                this
            }
            else -> {
                DefaultEntityRegistry(
                    entityIdentifierAttributePathsSet = entityIdentifierAttributePathsSet.add(path)
                )
            }
        }
    }

    override fun pathBelongsToEntityIdentifierAttributeVertex(path: GQLOperationPath): Boolean {
        return entityIdentifierAttributePathsSet.contains(path)
    }

    override fun getAllPathsBelongingToEntityIdentifierAttributeVertices():
        ImmutableSet<GQLOperationPath> {
        return entityIdentifierAttributePathsSet
    }

    override fun pathBelongsToEntitySourceContainerTypeVertex(path: GQLOperationPath): Boolean {
        return entityIdentifierAttributePathsByParentPath.containsKey(path)
    }

    override fun getEntityIdentifierAttributeVerticesBelongingToSourceContainerIndexPath(
        path: GQLOperationPath
    ): ImmutableSet<GQLOperationPath> {
        return entityIdentifierAttributePathsByParentPath[path] ?: persistentSetOf()
    }

    override fun findNearestEntityIdentifierPathRelatives(
        path: GQLOperationPath
    ): ImmutableSet<GQLOperationPath> {
        return nearestEntityIdentifierAttributeRelativesMemoizer(path)
    }

    private fun nearestEntityIdentifierAttributeRelativesCalculator():
        (GQLOperationPath) -> PersistentSet<GQLOperationPath>? {
        return { path: GQLOperationPath ->
            when {
                path.isRoot() -> {
                    none()
                } // closest relative is itself
                path in entityIdentifierAttributePathsSet -> {
                    path.getParentPath().flatMap { pp ->
                        entityIdentifierAttributePathsByParentPath[pp].toOption()
                    }
                }
                else -> {
                    path
                        .some()
                        .filter { p -> p.argument.isEmpty() && p.directive.isEmpty() }
                        .orElse { path.transform { clearArgument().clearDirective() }.some() }
                        .recurse { p ->
                            p.getParentPath()
                                .flatMap { pp ->
                                    /* sibling if parent is the same, cousin if grandparent is the same, etc. */
                                    entityIdentifierAttributePathsByParentPath[pp].toOption()
                                }
                                .fold(
                                    { p.getParentPath().map { pp -> pp.left() } },
                                    { entityIdentifierAttributes ->
                                        entityIdentifierAttributes.right().some()
                                    }
                                )
                        }
                }
            }.orNull()
        }
    }

    override fun findNearestEntitySourceContainerTypeVertexAncestor(
        path: GQLOperationPath
    ): Option<GQLOperationPath> {
        return nearestEntitySourceContainerTypeVertexAncestorMemoizer(path)
    }

    private fun nearestEntitySourceContainerTypeVertexAncestorCalculator():
        (GQLOperationPath) -> GQLOperationPath? {
        return { path: GQLOperationPath ->
            when {
                path.isRoot() -> {
                    none()
                } // closest ancestor is its parent
                path in entityIdentifierAttributePathsSet -> {
                    path.getParentPath()
                }
                else -> {
                    path
                        .some()
                        .filter { p -> p.argument.isEmpty() && p.directive.isEmpty() }
                        .orElse { path.transform { clearArgument().clearDirective() }.some() }
                        .recurse { p ->
                            p.getParentPath()
                                .filter { pp ->
                                    /* sibling if parent is the same, cousin if grandparent is the same, etc. */
                                    pp in entityIdentifierAttributePathsByParentPath
                                }
                                .fold(
                                    { p.getParentPath().map { pp -> pp.left() } },
                                    { entityAncestorPath -> entityAncestorPath.right().some() }
                                )
                        }
                }
            }.orNull()
        }
    }
}

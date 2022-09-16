package funcify.feature.schema.directive.entity

import arrow.core.Option
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.none
import arrow.core.orElse
import arrow.core.right
import arrow.core.some
import arrow.core.toOption
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.extensions.OptionExtensions.recurse
import funcify.feature.tools.extensions.SequenceExtensions.flatMapOptions
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf

/**
 *
 * @author smccarron
 * @created 2022-09-15
 */
internal class DefaultEntityRegistry(
    private val entityIdentifierAttributePathsSet: PersistentSet<SchematicPath> = persistentSetOf()
) : EntityRegistry {

    private val entityIdentifierAttributePathsByParentPath:
        PersistentMap<SchematicPath, PersistentSet<SchematicPath>> by lazy {
        entityIdentifierAttributePathsSet
            .asSequence()
            .map { sp -> sp.getParentPath().map { pp -> pp to sp } }
            .flatMapOptions()
            .fold(persistentMapOf<SchematicPath, PersistentSet<SchematicPath>>()) { pm, (pp, cp) ->
                pm.put(pp, pm.getOrElse(pp) { persistentSetOf() }.add(cp))
            }
    }

    private val nearestEntityIdentifierAttributeRelativesMemoizer:
        (SchematicPath) -> PersistentSet<SchematicPath> by lazy {
        val cache: ConcurrentMap<SchematicPath, PersistentSet<SchematicPath>> = ConcurrentHashMap()
        ({ path: SchematicPath ->
            cache
                .computeIfAbsent(path, nearestEntityIdentifierAttributeRelativesCalculator())
                .toOption()
                .getOrElse { persistentSetOf() }
        })
    }

    private val nearestEntitySourceContainerTypeVertexAncestorMemoizer:
        (SchematicPath) -> Option<SchematicPath> by lazy {
        val cache: ConcurrentMap<SchematicPath, SchematicPath> = ConcurrentHashMap()
        ({ path: SchematicPath ->
            cache
                .computeIfAbsent(path, nearestEntitySourceContainerTypeVertexAncestorCalculator())
                .toOption()
        })
    }

    override fun registerSchematicPathAsMappingToEntityIdentifierAttributeVertex(
        path: SchematicPath
    ): EntityRegistry {
        return when {
            // Path cannot represent a parameter vertex, as even parameter containers are not
            // required in any sense to have unique identifier attributes
            path.arguments.isNotEmpty() || path.directives.isNotEmpty() -> {
                this
            }
            else -> {
                DefaultEntityRegistry(
                    entityIdentifierAttributePathsSet = entityIdentifierAttributePathsSet.add(path)
                )
            }
        }
    }

    override fun pathBelongsToEntityIdentifierAttributeVertex(path: SchematicPath): Boolean {
        return entityIdentifierAttributePathsSet.contains(path)
    }

    override fun getAllPathsBelongingToEntityIdentifierAttributeVertices():
        ImmutableSet<SchematicPath> {
        return entityIdentifierAttributePathsSet
    }

    override fun pathBelongsToEntitySourceContainerTypeVertex(path: SchematicPath): Boolean {
        return entityIdentifierAttributePathsByParentPath.containsKey(path)
    }

    override fun findNearestEntityIdentifierPathRelatives(
        path: SchematicPath
    ): ImmutableSet<SchematicPath> {
        return nearestEntityIdentifierAttributeRelativesMemoizer(path)
    }

    private fun nearestEntityIdentifierAttributeRelativesCalculator():
        (SchematicPath) -> PersistentSet<SchematicPath>? {
        return { path: SchematicPath ->
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
                        .filter { p -> p.arguments.isEmpty() && p.directives.isEmpty() }
                        .orElse { path.transform { clearArguments().clearDirectives() }.some() }
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
        path: SchematicPath
    ): Option<SchematicPath> {
        return nearestEntitySourceContainerTypeVertexAncestorMemoizer(path)
    }

    private fun nearestEntitySourceContainerTypeVertexAncestorCalculator():
        (SchematicPath) -> SchematicPath? {
        return { path: SchematicPath ->
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
                        .filter { p -> p.arguments.isEmpty() && p.directives.isEmpty() }
                        .orElse { path.transform { clearArguments().clearDirectives() }.some() }
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

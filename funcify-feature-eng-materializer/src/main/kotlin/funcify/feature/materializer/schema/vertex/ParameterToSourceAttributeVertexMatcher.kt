package funcify.feature.materializer.schema.vertex

import arrow.core.Option
import arrow.core.filterIsInstance
import arrow.core.firstOrNone
import arrow.core.getOrElse
import arrow.core.orElse
import arrow.core.toOption
import funcify.feature.materializer.schema.MaterializationMetamodel
import funcify.feature.schema.MetamodelGraph
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.vertex.ParameterAttributeVertex
import funcify.feature.schema.vertex.SourceAttributeVertex
import funcify.feature.schema.vertex.SourceContainerTypeVertex
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

internal object ParameterToSourceAttributeVertexMatcher :
        (MaterializationMetamodel, GQLOperationPath) -> Option<SourceAttributeVertex> {

    private val parameterToSourceVertexMemoizer:
                (MaterializationMetamodel, GQLOperationPath) -> Option<SourceAttributeVertex> by lazy {
        val cache: ConcurrentMap<Pair<Instant, GQLOperationPath>, SourceAttributeVertex> =
            ConcurrentHashMap()
        ({ mmg, paramPath ->
            cache
                .computeIfAbsent(
                    mmg.created to paramPath,
                    sourceAttributeVertexWithSameNameCalculator(mmg)
                )
                .toOption()
        })
    }

    override fun invoke(
        materializationMetamodel: MaterializationMetamodel,
        parameterVertexPath: GQLOperationPath
    ): Option<SourceAttributeVertex> {
        return parameterToSourceVertexMemoizer(materializationMetamodel, parameterVertexPath)
    }

    private fun sourceAttributeVertexWithSameNameCalculator(
        materializationMetamodel: MaterializationMetamodel
    ): (Pair<Instant, GQLOperationPath>) -> SourceAttributeVertex? {
        return { (materializationMetamodelCreated: Instant, parameterPath: GQLOperationPath) ->
            val metamodelGraph: MetamodelGraph = materializationMetamodel.metamodelGraph
            parameterPath
                .toOption()
                .filter { paramPath -> paramPath.arguments.isNotEmpty() }
                .flatMap { paramPath -> metamodelGraph.pathBasedGraph.getVertex(paramPath) }
                .filterIsInstance<ParameterAttributeVertex>()
                .flatMap { pav: ParameterAttributeVertex ->
                    findSourceAttributeVertexWithSameNameInSameDomain(pav, metamodelGraph)
                        .orElse {
                            findSourceAttributeVertexByAliasReferenceInSameDomain(
                                pav,
                                metamodelGraph
                            )
                        }
                        .orElse {
                            findSourceAttributeVertexWithSameNameInDifferentDomain(
                                pav,
                                metamodelGraph
                            )
                        }
                        .orElse {
                            findSourceAttributeVertexByAliasReferenceInDifferentDomain(
                                pav,
                                metamodelGraph
                            )
                        }
                }
                .orNull()
        }
    }

    private fun <
        V : ParameterAttributeVertex> findSourceAttributeVertexWithSameNameInDifferentDomain(
        vertex: V,
        metamodelGraph: MetamodelGraph
    ): Option<SourceAttributeVertex> {
        return vertex.compositeParameterAttribute.conventionalName.qualifiedForm
            .toOption()
            .flatMap { name ->
                metamodelGraph.sourceAttributeVerticesByQualifiedName[name].toOption()
            }
            .flatMap { srcAttrs ->
                vertex.path.pathSegments.firstOrNone().flatMap { domainPathSegment ->
                    srcAttrs
                        .asSequence()
                        .firstOrNull { sav: SourceAttributeVertex ->
                            sav.path.pathSegments
                                .firstOrNone { firstPathSegment ->
                                    firstPathSegment != domainPathSegment
                                }
                                .isDefined()
                        }
                        .toOption()
                }
            }
    }

    private fun <V : ParameterAttributeVertex> findSourceAttributeVertexWithSameNameInSameDomain(
        vertex: V,
        metamodelGraph: MetamodelGraph
    ): Option<SourceAttributeVertex> {
        return vertex.compositeParameterAttribute.conventionalName.qualifiedForm
            .toOption()
            .flatMap { name ->
                metamodelGraph.sourceAttributeVerticesByQualifiedName[name].toOption()
            }
            .flatMap { srcAttrs ->
                vertex.path.pathSegments.firstOrNone().flatMap { domainPathSegment ->
                    srcAttrs
                        .asSequence()
                        .firstOrNull { sav: SourceAttributeVertex ->
                            sav.path.pathSegments
                                .firstOrNone { firstPathSegment ->
                                    firstPathSegment == domainPathSegment
                                }
                                .isDefined()
                        }
                        .toOption()
                }
            }
    }

    private fun <
        V : ParameterAttributeVertex> findSourceAttributeVertexByAliasReferenceInSameDomain(
        vertex: V,
        metamodelGraph: MetamodelGraph
    ): Option<SourceAttributeVertex> {
        return vertex.compositeParameterAttribute.conventionalName.qualifiedForm
            .toOption()
            .flatMap { name ->
                metamodelGraph.attributeAliasRegistry.getSourceVertexPathWithSimilarNameOrAlias(
                    name
                )
            }
            .flatMap { srcAttrPath ->
                getSourceAttributeVerticesWithSameParentTypeAndAttributeNameForPath(
                        srcAttrPath,
                        metamodelGraph
                    )
                    .let { srcAttrs ->
                        vertex.path.pathSegments.firstOrNone().flatMap {
                            currentParamPathDomainSegment ->
                            srcAttrs
                                .asSequence()
                                .firstOrNull { sav ->
                                    sav.path.pathSegments
                                        .firstOrNone()
                                        .filter { sourceAttributeDomainPathSegment ->
                                            currentParamPathDomainSegment ==
                                                sourceAttributeDomainPathSegment
                                        }
                                        .isDefined()
                                }
                                .toOption()
                        }
                    }
            }
    }

    private fun getSourceAttributeVerticesWithSameParentTypeAndAttributeNameForPath(
        sourceAttributePath: GQLOperationPath,
        metamodelGraph: MetamodelGraph,
    ): ImmutableSet<SourceAttributeVertex> {
        return sourceAttributePath
            .getParentPath()
            .flatMap { pp ->
                metamodelGraph.pathBasedGraph
                    .getVertex(pp)
                    .filterIsInstance<SourceContainerTypeVertex>()
                    .map { sct: SourceContainerTypeVertex ->
                        sct.compositeContainerType.conventionalName.qualifiedForm
                    }
                    .zip(
                        metamodelGraph.pathBasedGraph
                            .getVertex(sourceAttributePath)
                            .filterIsInstance<SourceAttributeVertex>()
                            .map { sav: SourceAttributeVertex ->
                                sav.compositeAttribute.conventionalName.qualifiedForm
                            }
                    )
                    .flatMap { parentTypeSrcAttrName ->
                        metamodelGraph
                            .sourceAttributeVerticesWithParentTypeAttributeQualifiedNamePair[
                                parentTypeSrcAttrName]
                            .toOption()
                    }
            }
            .getOrElse { persistentSetOf() }
    }

    private fun <
        V : ParameterAttributeVertex> findSourceAttributeVertexByAliasReferenceInDifferentDomain(
        vertex: V,
        metamodelGraph: MetamodelGraph
    ): Option<SourceAttributeVertex> {
        return vertex.compositeParameterAttribute.conventionalName.qualifiedForm
            .toOption()
            .flatMap { name ->
                metamodelGraph.attributeAliasRegistry.getSourceVertexPathWithSimilarNameOrAlias(
                    name
                )
            }
            .flatMap { srcAttrPath ->
                getSourceAttributeVerticesWithSameParentTypeAndAttributeNameForPath(
                        srcAttrPath,
                        metamodelGraph
                    )
                    .let { srcAttrs ->
                        vertex.path.pathSegments.firstOrNone().flatMap {
                            currentParamPathDomainSegment ->
                            srcAttrs
                                .asSequence()
                                .firstOrNull { sav ->
                                    sav.path.pathSegments
                                        .firstOrNone()
                                        .filter { sourceAttributeDomainPathSegment ->
                                            currentParamPathDomainSegment !=
                                                sourceAttributeDomainPathSegment
                                        }
                                        .isDefined()
                                }
                                .toOption()
                        }
                    }
            }
    }
}

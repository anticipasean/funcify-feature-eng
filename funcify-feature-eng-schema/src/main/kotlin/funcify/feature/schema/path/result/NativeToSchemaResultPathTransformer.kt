package funcify.feature.schema.path.result

import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import arrow.core.some
import arrow.core.toOption
import funcify.feature.tools.extensions.OptionExtensions.recurse
import graphql.execution.ResultPath
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

internal object NativeToSchemaResultPathTransformer : (ResultPath) -> GQLResultPath {

    private val cache: ConcurrentMap<ResultPath, GQLResultPath> = ConcurrentHashMap()

    override fun invoke(nativePath: ResultPath): GQLResultPath {
        return cache.computeIfAbsent(nativePath) { rp: ResultPath ->
            GQLResultPath.of(extractElementSegmentsFromNativePath(rp))
        }
    }

    private fun extractElementSegmentsFromNativePath(
        nativePath: ResultPath?
    ): (GQLResultPath.Builder) -> GQLResultPath.Builder {
        return { builder: GQLResultPath.Builder ->
            (builder to nativePath)
                .toOption()
                .recurse { (b: GQLResultPath.Builder, rp: ResultPath?) ->
                    when {
                        rp == null || rp.isRootPath -> {
                            b.right().some()
                        }
                        rp.isNamedSegment -> {
                            (b.prependNamedSegment(rp.segmentName) to rp.parent).left().some()
                        }
                        else -> {
                            val parentOfPath: ResultPath? = rp.parent
                            when {
                                parentOfPath == null || parentOfPath.isRootPath -> {
                                    b.prependUnnamedListSegment(rp.segmentIndex).right().some()
                                }
                                parentOfPath.isNamedSegment -> {
                                    (b.prependNamedListSegment(
                                            parentOfPath.segmentName,
                                            rp.segmentIndex
                                        ) to parentOfPath.parent)
                                        .left()
                                        .some()
                                }
                                else -> {
                                    (b.prependUnnamedListSegment(rp.segmentIndex) to parentOfPath)
                                        .left()
                                        .some()
                                }
                            }
                        }
                    }
                }
                .getOrElse { builder }
        }
    }
}

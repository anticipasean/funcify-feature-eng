package funcify.feature.schema.path.result

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
            prependEachSegmentUntilRootResultPathReached(builder, nativePath)
        }
    }

    private tailrec fun prependEachSegmentUntilRootResultPathReached(
        b: GQLResultPath.Builder,
        rp: ResultPath?
    ): GQLResultPath.Builder {
        return when {
            rp == null || rp.isRootPath -> {
                b
            }
            rp.isNamedSegment -> {
                prependEachSegmentUntilRootResultPathReached(
                    b.prependNamedSegment(rp.segmentName),
                    rp.parent
                )
            }
            else -> {
                val parentOfPath: ResultPath? = rp.parent
                when {
                    parentOfPath == null || parentOfPath.isRootPath -> {
                        b.prependUnnamedListSegment(rp.segmentIndex)
                    }
                    parentOfPath.isNamedSegment -> {
                        prependEachSegmentUntilRootResultPathReached(
                            b.prependNamedListSegment(parentOfPath.segmentName, rp.segmentIndex),
                            parentOfPath.parent
                        )
                    }
                    else -> {
                        prependEachSegmentUntilRootResultPathReached(
                            b.prependUnnamedListSegment(rp.segmentIndex),
                            parentOfPath
                        )
                    }
                }
            }
        }
    }
}

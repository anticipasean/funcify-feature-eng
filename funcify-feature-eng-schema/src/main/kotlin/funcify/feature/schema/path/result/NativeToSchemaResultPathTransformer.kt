package funcify.feature.schema.path.result

import graphql.execution.ResultPath
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf

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
            prependEachSegmentUntilRootResultPathReached(builder, nativePath, null)
        }
    }

    private tailrec fun prependEachSegmentUntilRootResultPathReached(
        b: GQLResultPath.Builder,
        rp: ResultPath?,
        indexSegmentsEncountered: PersistentList<Int>?
    ): GQLResultPath.Builder {
        return when (rp) {
            null -> {
                when (indexSegmentsEncountered) {
                    null -> {
                        b
                    }
                    else -> {
                        b.prependNestedListSegment("", indexSegmentsEncountered)
                    }
                }
            }
            else -> {
                when (indexSegmentsEncountered) {
                    null -> {
                        when {
                            rp.isRootPath -> {
                                b
                            }
                            rp.isNamedSegment -> {
                                prependEachSegmentUntilRootResultPathReached(
                                    b.prependNamedSegment(rp.segmentName),
                                    rp.parent,
                                    null
                                )
                            }
                            else -> {
                                prependEachSegmentUntilRootResultPathReached(
                                    b,
                                    rp.parent,
                                    persistentListOf(rp.segmentIndex)
                                )
                            }
                        }
                    }
                    else -> {
                        when {
                            rp.isRootPath -> {
                                b.prependNestedListSegment("", indexSegmentsEncountered)
                            }
                            rp.isNamedSegment -> {
                                prependEachSegmentUntilRootResultPathReached(
                                    b.prependNestedListSegment(
                                        rp.segmentName,
                                        indexSegmentsEncountered
                                    ),
                                    rp.parent,
                                    null
                                )
                            }
                            else -> {
                                prependEachSegmentUntilRootResultPathReached(
                                    b,
                                    rp.parent,
                                    indexSegmentsEncountered.add(0, rp.segmentIndex)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

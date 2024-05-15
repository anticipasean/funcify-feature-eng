package funcify.feature.tree.path

import kotlinx.collections.immutable.ImmutableList

internal object TreePathComparator : Comparator<TreePath> {

    override fun compare(o1: TreePath?, o2: TreePath?): Int {
        return when (o1) {
            null -> {
                when (o2) {
                    null -> 0
                    else -> -1
                }
            }
            else -> {
                when (o2) {
                    null -> 1
                    else -> compareNonNullPaths(o1, o2)
                }
            }
        }
    }

    private fun compareNonNullPaths(p1: TreePath, p2: TreePath): Int {
        return when (val schemeComp: Int = p1.scheme.compareTo(p2.scheme)) {
            0 -> {
                comparePathSegments(p1.pathSegments, p2.pathSegments)
            }
            else -> {
                schemeComp
            }
        }
    }

    private fun comparePathSegments(
        ps1: ImmutableList<PathSegment>,
        ps2: ImmutableList<PathSegment>
    ): Int {
        val sizeComp: Int = ps1.size.compareTo(ps2.size)
        return ps1.asSequence()
            .zip(ps2.asSequence()) { s1: PathSegment, s2: PathSegment ->
                when (s1) {
                    is NameSegment -> {
                        when (s2) {
                            is NameSegment -> {
                                s1.name.compareTo(s2.name)
                            }
                            is IndexSegment -> {
                                1
                            }
                        }
                    }
                    is IndexSegment -> {
                        when (s2) {
                            is NameSegment -> {
                                -1
                            }
                            is IndexSegment -> {
                                s1.index.compareTo(s2.index)
                            }
                        }
                    }
                }
            }
            .firstOrNull { comp: Int -> comp != 0 }
            ?: sizeComp
    }
}

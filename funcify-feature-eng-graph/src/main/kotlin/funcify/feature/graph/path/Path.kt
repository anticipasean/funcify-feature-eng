package funcify.feature.graph.path

/**
 * @param P
 * - "Point" Type
 * @author smccarron
 * @created 2023-01-03
 */
sealed interface Path<P : Comparable<P>> : Comparable<Path<P>> {

    operator fun component1(): P

    operator fun component2(): P

    override fun compareTo(other: Path<P>): Int {
        return when (val firstComparison: Int = this.component1().compareTo(other.component1())) {
            0 -> {
                this.component2().compareTo(other.component2())
            }
            else -> {
                firstComparison
            }
        }
    }

    interface UndirectedPath<P : Comparable<P>> : Path<P> {
        val firstPoint: P
        val secondPoint: P

        override fun component1(): P {
            return firstPoint
        }

        override fun component2(): P {
            return secondPoint
        }
    }

    interface DirectedPath<P : Comparable<P>> : Path<P> {
        val sourcePoint: P
        val destinationPoint: P

        override fun component1(): P {
            return sourcePoint
        }

        override fun component2(): P {
            return destinationPoint
        }
    }
}

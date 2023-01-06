package funcify.feature.graph.line

/**
 *
 * @author smccarron
 * @created 2023-01-05
 */
internal class DefaultDirectedLine<out P>(
    override val sourcePoint: P,
    override val destinationPoint: P
) : DirectedLine<P> {

    override fun swap(): Line<P> {
        return DefaultDirectedLine(destinationPoint, sourcePoint)
    }

    override fun equals(other: Any?): Boolean {
        return when (other) {
            null -> {
                false
            }
            is DirectedLine<*> -> {
                this.sourcePoint == other.sourcePoint &&
                    this.destinationPoint == other.destinationPoint
            }
            else -> {
                false
            }
        }
    }

    override fun hashCode(): Int {
        return (sourcePoint.hashCode() * 31) + (destinationPoint.hashCode() * 7)
    }

    override fun toString(): String {
        return "($sourcePoint => $destinationPoint)"
    }
}

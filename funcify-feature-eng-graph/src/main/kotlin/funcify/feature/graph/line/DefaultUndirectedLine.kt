package funcify.feature.graph.line

/**
 *
 * @author smccarron
 * @created 2023-01-05
 */
internal class DefaultUndirectedLine<out P>(
    override val firstPoint: P,
    override val secondPoint: P
) : UndirectedLine<P> {

    override fun swap(): Line<P> {
        return DefaultUndirectedLine<P>(secondPoint, firstPoint)
    }

    override fun equals(other: Any?): Boolean {
        return when (other) {
            null -> {
                false
            }
            is UndirectedLine<*> -> {
                (this.firstPoint == other.firstPoint && this.secondPoint == other.secondPoint) ||
                    (this.firstPoint == other.secondPoint && this.secondPoint == other.firstPoint)
            }
            else -> {
                false
            }
        }
    }

    override fun hashCode(): Int {
        return firstPoint.hashCode() + secondPoint.hashCode()
    }

    override fun toString(): String {
        return "($firstPoint, $secondPoint)"
    }
}

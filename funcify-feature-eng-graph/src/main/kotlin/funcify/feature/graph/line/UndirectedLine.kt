package funcify.feature.graph.line

interface UndirectedLine<out P> : Line<P> {

    companion object {

        fun <P> of(firstPoint: P, secondPoint: P): UndirectedLine<P> {
            return DefaultUndirectedLine<P>(firstPoint = firstPoint, secondPoint = secondPoint)
        }
    }

    val firstPoint: P

    val secondPoint: P

    override fun component1(): P {
        return firstPoint
    }

    override fun component2(): P {
        return secondPoint
    }
}

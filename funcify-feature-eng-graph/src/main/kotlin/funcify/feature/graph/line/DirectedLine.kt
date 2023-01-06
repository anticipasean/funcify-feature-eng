package funcify.feature.graph.line

interface DirectedLine<out P> : Line<P> {

    companion object {

        fun <P> of(sourcePoint: P, destinationPoint: P): DirectedLine<P> {
            return DefaultDirectedLine<P>(
                sourcePoint = sourcePoint,
                destinationPoint = destinationPoint
            )
        }
    }

    val sourcePoint: P

    val destinationPoint: P

    override fun component1(): P {
        return sourcePoint
    }

    override fun component2(): P {
        return destinationPoint
    }
}

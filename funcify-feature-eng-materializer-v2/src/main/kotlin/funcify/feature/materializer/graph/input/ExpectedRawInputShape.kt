package funcify.feature.materializer.graph.input

/**
 * @author smccarron
 * @created 2023-08-05
 */
sealed interface ExpectedRawInputShape {

    val rawInputContextShape: RawInputContextShape

    interface StandardJsonInputShape : ExpectedRawInputShape {

        override val rawInputContextShape: RawInputContextShape
            get() = standardJsonShape

        val standardJsonShape: RawInputContextShape.Tree
    }

    interface TabularInputShape : ExpectedRawInputShape {

        override val rawInputContextShape: RawInputContextShape
            get() = tabularShape

        val tabularShape: RawInputContextShape.Tabular
    }
}

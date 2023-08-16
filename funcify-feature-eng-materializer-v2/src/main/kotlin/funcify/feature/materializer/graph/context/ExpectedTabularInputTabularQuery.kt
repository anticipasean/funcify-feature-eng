package funcify.feature.materializer.graph.context

import funcify.feature.materializer.graph.input.ExpectedRawInputShape
import funcify.feature.materializer.graph.input.RawInputContextShape
import funcify.feature.materializer.graph.target.TabularQueryTarget
import kotlinx.collections.immutable.ImmutableSet

interface ExpectedTabularInputTabularQuery :
    RequestMaterializationGraphContext,
    ExpectedRawInputShape.TabularInputShape,
    TabularQueryTarget {

    override val outputColumnNames: ImmutableSet<String>

    override val tabularShape: RawInputContextShape.Tabular

    fun update(transformer: Builder.() -> Builder): ExpectedTabularInputTabularQuery

    interface Builder : RequestMaterializationGraphContext.Builder<Builder> {

        fun outputColumnNames(outputColumnNames: ImmutableSet<String>): Builder

        fun tabularShape(tabularShape: RawInputContextShape.Tabular): Builder

        fun build(): ExpectedTabularInputTabularQuery

    }
}

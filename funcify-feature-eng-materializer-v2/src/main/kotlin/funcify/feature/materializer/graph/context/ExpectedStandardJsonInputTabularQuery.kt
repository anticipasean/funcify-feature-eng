package funcify.feature.materializer.graph.context

import funcify.feature.materializer.graph.input.ExpectedRawInputShape
import funcify.feature.materializer.graph.input.RawInputContextShape
import funcify.feature.materializer.graph.target.TabularQueryTarget
import graphql.language.Document
import kotlinx.collections.immutable.ImmutableSet

interface ExpectedStandardJsonInputTabularQuery :
    RequestMaterializationGraphContext,
    ExpectedRawInputShape.StandardJsonInputShape,
    TabularQueryTarget {

    override val outputColumnNames: ImmutableSet<String>

    override val standardJsonShape: RawInputContextShape.Tree

    fun update(transformer: Builder.() -> Builder): ExpectedStandardJsonInputTabularQuery

    interface Builder : RequestMaterializationGraphContext.Builder<Builder> {

        fun outputColumnNames(outputColumnNames: ImmutableSet<String>): Builder

        fun standardJsonShape(treeShape: RawInputContextShape.Tree): Builder

        fun build(): ExpectedStandardJsonInputTabularQuery

    }
}

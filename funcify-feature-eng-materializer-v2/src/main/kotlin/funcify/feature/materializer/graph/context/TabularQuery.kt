package funcify.feature.materializer.graph.context

import funcify.feature.materializer.graph.target.TabularQueryTarget
import kotlinx.collections.immutable.ImmutableSet

interface TabularQuery : RequestMaterializationGraphContext, TabularQueryTarget {

    override val outputColumnNames: ImmutableSet<String>

    val unhandledOutputColumnNames: ImmutableSet<String>

    fun update(transformer: Builder.() -> Builder): TabularQuery

    interface Builder : RequestMaterializationGraphContext.Builder<Builder> {

        fun outputColumnNames(outputColumnNames: ImmutableSet<String>): Builder

        fun addUnhandledColumnName(unhandledColumnName: String): Builder

        fun dropHeadUnhandledColumnName(): Builder

        fun build(): TabularQuery
    }
}

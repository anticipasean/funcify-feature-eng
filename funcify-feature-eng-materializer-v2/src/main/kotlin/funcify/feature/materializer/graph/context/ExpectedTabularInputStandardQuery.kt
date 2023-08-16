package funcify.feature.materializer.graph.context

import funcify.feature.materializer.graph.input.ExpectedRawInputShape
import funcify.feature.materializer.graph.input.RawInputContextShape
import funcify.feature.materializer.graph.target.StandardQueryTarget
import graphql.language.Document
import kotlinx.collections.immutable.ImmutableSet

interface ExpectedTabularInputStandardQuery :
    RequestMaterializationGraphContext,
    StandardQueryTarget,
    ExpectedRawInputShape.TabularInputShape {

    override val operationName: String

    override val document: Document

    override val tabularShape: RawInputContextShape.Tabular

    fun update(transformer: Builder.() -> Builder): ExpectedTabularInputStandardQuery

    interface Builder : RequestMaterializationGraphContext.Builder<Builder> {

        fun operationName(operationName: String): Builder

        fun outputColumnNames(outputColumnNames: ImmutableSet<String>): Builder

        fun tabularShape(tabularShape: RawInputContextShape.Tabular): Builder

        fun build(): ExpectedTabularInputStandardQuery

    }
}

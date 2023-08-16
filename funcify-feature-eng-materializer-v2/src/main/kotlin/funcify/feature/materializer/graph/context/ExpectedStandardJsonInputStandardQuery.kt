package funcify.feature.materializer.graph.context

import funcify.feature.materializer.graph.input.ExpectedRawInputShape
import funcify.feature.materializer.graph.input.RawInputContextShape
import funcify.feature.materializer.graph.target.StandardQueryTarget
import graphql.language.Document

interface ExpectedStandardJsonInputStandardQuery :
    RequestMaterializationGraphContext,
    StandardQueryTarget,
    ExpectedRawInputShape.StandardJsonInputShape {

    override val operationName: String

    override val document: Document

    override val standardJsonShape: RawInputContextShape.Tree

    fun update(transformer: Builder.() -> Builder): ExpectedStandardJsonInputStandardQuery

    interface Builder : RequestMaterializationGraphContext.Builder<Builder> {

        fun operationName(operationName: String): Builder

        fun document(document: Document): Builder

        fun standardJsonShape(treeShape: RawInputContextShape.Tree): Builder

        fun build(): ExpectedStandardJsonInputStandardQuery

    }
}

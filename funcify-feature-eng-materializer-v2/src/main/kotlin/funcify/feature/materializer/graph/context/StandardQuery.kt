package funcify.feature.materializer.graph.context

import funcify.feature.materializer.graph.target.StandardQueryTarget
import graphql.language.Document

interface StandardQuery : RequestMaterializationGraphContext, StandardQueryTarget {

    override val operationName: String

    override val document: Document

    fun update(transformer: Builder.() -> Builder): StandardQuery

    interface Builder: RequestMaterializationGraphContext.Builder<Builder> {

        fun operationName(operationName: String): Builder

        fun document(document: Document): Builder

        fun build(): StandardQuery
    }
}

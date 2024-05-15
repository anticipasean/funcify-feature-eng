package funcify.feature.materializer.graph.context

import graphql.language.Document

interface StandardQuery : RequestMaterializationGraphContext {

    val operationName: String

    val document: Document

    fun update(transformer: Builder.() -> Builder): StandardQuery

    interface Builder : RequestMaterializationGraphContext.Builder<Builder> {

        fun operationName(operationName: String): Builder

        fun document(document: Document): Builder

        fun build(): StandardQuery
    }
}

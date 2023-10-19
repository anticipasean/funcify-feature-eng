package funcify.feature.schema.document

import funcify.feature.tools.container.attempt.Try
import graphql.language.Document
import graphql.schema.GraphQLSchema

/**
 * @author smccarron
 * @created 2023-10-12
 */
interface GQLDocumentComposer {

    companion object {

        fun defaultComposer(): GQLDocumentComposer {
            return DefaultGQLDocumentComposer
        }
    }

    fun composeDocumentFromSpecWithSchema(
        spec: GQLDocumentSpec,
        graphQLSchema: GraphQLSchema
    ): Try<Document>
}

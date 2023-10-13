package funcify.feature.materializer.gql

import funcify.feature.materializer.model.MaterializationMetamodel
import funcify.feature.tools.container.attempt.Try
import graphql.language.Document
import graphql.schema.GraphQLSchema

/**
 * @author smccarron
 * @created 2023-10-12
 */
interface GQLDocumentComposer {

    fun composeDocumentFromSpecWithMetamodel(
        spec: GQLDocumentSpec,
        materializationMetamodel: MaterializationMetamodel
    ): Try<Document>

    fun composeDocumentFromSpecWithSchema(
        spec: GQLDocumentSpec,
        graphQLSchema: GraphQLSchema
    ): Try<Document>
}

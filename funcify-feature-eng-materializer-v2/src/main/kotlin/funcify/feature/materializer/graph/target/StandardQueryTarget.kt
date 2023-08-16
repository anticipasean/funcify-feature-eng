package funcify.feature.materializer.graph.target

import graphql.language.Document

/**
 *
 * @author smccarron
 * @created 2023-08-05
 */
interface StandardQueryTarget {

    val operationName: String

    val document: Document

}

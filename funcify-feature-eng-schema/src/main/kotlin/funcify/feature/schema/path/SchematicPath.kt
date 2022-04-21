package funcify.feature.schema.path

import arrow.core.Option
import arrow.core.none
import arrow.core.some
import java.net.URI
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap

/**
 * Represents a feature function within the schema, its arguments / parameters, and any directives
 * specifying additional contextual information or processing steps
 *
 * @author smccarron
 * @created 1/30/22
 */
interface SchematicPath {

    companion object {}

    /**
     * Represented by URI path segments `/user/transactions/messageBody` in URI form and a
     * query/mutation/subscription graph structure:
     * ```
     * user(id: 123) {
     *     transactions(filter: { correlation_id: { eq: "82b1d1cd-8020-41f1-9536-dc143c320ff1" } }) {
     *         messageBody
     *     }
     * }
     * ``` in GraphQL query form
     * ```
     */
    val pathSegments: ImmutableList<String>

    /**
     * Represented by URI query parameters
     * `?correlation_id=82b1d1cd-8020-41f1-9536-dc143c320ff1&user_id=123` in URI form and contextual
     * input arguments `node(context: {"correlation_id":
     * "82b1d1cd-8020-41f1-9536-dc143c320ff1","user_id": 123})` in GraphQL SDL form
     */
    val arguments: ImmutableMap<String, String>

    /**
     * Represented by URI fragments `#uppercase&aliases=names=amount_remaining_3m_1m,amt_rem_3m1m`
     * in URI form and schema directives `@uppercase @aliases(names: ["amount_remaining_3m_1m",
     * "amt_rem_3m1m" ])` in GraphQL SDL form
     */
    val directives: ImmutableMap<String, String>

    /** URI representation of path on which feature function is located within service context */
    fun toURI(): URI

    fun isRoot(): Boolean {
        return pathSegments.isEmpty()
    }

    fun isParentTo(other: SchematicPath): Boolean {
        return when {
            this.pathSegments.size >= other.pathSegments.size -> {
                false
            }
            this.pathSegments.size + 1 == other.pathSegments.size -> {
                pathSegments
                    .asSequence()
                    .zip(other.pathSegments.asSequence()) { a: String, b: String -> a == b }
                    .all { matched -> matched }
            }
            else -> {
                false
            }
        }
    }
    fun isChildTo(other: SchematicPath): Boolean {
        return when {
            this.pathSegments.size <= other.pathSegments.size -> {
                false
            }
            this.pathSegments.size - 1 == other.pathSegments.size -> {
                other
                    .pathSegments
                    .asSequence()
                    .zip(this.pathSegments.asSequence()) { a: String, b: String -> a == b }
                    .all { matched -> matched }
            }
            else -> {
                false
            }
        }
    }

    fun isSiblingTo(other: SchematicPath): Boolean {
        return when {
            /** Is root a sibling to itself? Yes??? */
            this.pathSegments.size == 0 && this.pathSegments.size == 0 -> {
                true
            }
            this.pathSegments.size != other.pathSegments.size -> {
                false
            }
            this.pathSegments.size == 1 && other.pathSegments.size == 1 -> {
                true
            }
            this.pathSegments.size == other.pathSegments.size -> {
                /** Assumes path_segments.size must be greater than 1 if here */
                val parentPathSegmentsSize = pathSegments.size - 1
                pathSegments
                    .asSequence()
                    .take(parentPathSegmentsSize)
                    .zip(other.pathSegments.asSequence().take(parentPathSegmentsSize)) {
                        a: String,
                        b: String ->
                        a == b
                    }
                    .all { matched -> matched }
            }
            else -> {
                false
            }
        }
    }

    fun prependPathSegment(pathSegment: String): SchematicPath

    fun appendPathSegment(pathSegment: String): SchematicPath

    fun dropPathSegment(): SchematicPath

    fun getParentPath(): Option<SchematicPath> {
        return when {
            isRoot() -> {
                none<SchematicPath>()
            }
            else -> dropPathSegment().some()
        }
    }
}

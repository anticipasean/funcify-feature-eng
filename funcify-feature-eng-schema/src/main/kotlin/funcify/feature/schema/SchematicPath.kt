package funcify.feature.schema

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import org.springframework.web.util.UriComponents
import org.springframework.web.util.UriTemplate
import java.net.URI


/**
 * Represents a feature function within the schema, its arguments / parameters, and any directives specifying additional
 * contextual information or processing steps
 *
 * @author smccarron
 * @created 1/30/22
 */
interface SchematicPath {

    /**
     * Represented by URI path segments `/user/transactions/messageBody` in URI form and a query/mutation/subscription graph
     * structure:
     * ```
     * user(id: 123) {
     *     transactions(filter: { correlation_id: { eq: "82b1d1cd-8020-41f1-9536-dc143c320ff1" } }) {
     *         messageBody
     *     }
     * }
     * ``` in GraphQL query form
     */
    val pathSegments: ImmutableList<String>

    /**
     * Represented by URI query parameters `?correlation_id=82b1d1cd-8020-41f1-9536-dc143c320ff1&user_id=123` in URI form
     * and contextual input arguments `node(context: {"correlation_id": "82b1d1cd-8020-41f1-9536-dc143c320ff1","user_id": 123})`
     * in GraphQL SDL form
     */
    val arguments: ImmutableMap<String, String>

    /**
     * Represented by URI fragments `#uppercase&aliases=names=amount_remaining_3m_1m,amt_rem_3m1m` in URI form
     * and schema directives `@uppercase @aliases(names: ["amount_remaining_3m_1m", "amt_rem_3m1m" ])` in GraphQL SDL form
     */
    val directives: ImmutableMap<String, String>

    /**
     * URI representation of path on which feature function is located within service context
     */
    fun toURI(): URI

}
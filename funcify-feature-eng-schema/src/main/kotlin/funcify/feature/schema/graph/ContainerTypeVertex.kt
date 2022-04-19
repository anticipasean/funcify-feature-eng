package funcify.feature.schema.graph

import funcify.feature.schema.CompositeContainerType
import funcify.feature.schema.SchematicVertex

/**
 * Represents an object type within a graph, the attribute vertices of which representing different
 * feature functions this object type provides the context for: e.g. `user/applications` where
 * `user` is a type vertex and `applications` is an attribute feature function on that type The
 * context of the object type e.g. `user/applications?user_id=123&application_id=345` enables the
 * feature function to be resolved using contextual parameters: e.g.
 * ```
 * user(user_id: 123){
 *     applications(application_id: 345) {
 *         requestedAmount
 *     }
 * }
 * -> getRequestedAmountOnApplicationForUser(userId=123, applicationId=345)
 * -> requestedAmount => 7000.00
 * ```
 * @author smccarron
 * @created 1/30/22
 */
interface ContainerTypeVertex : SchematicVertex {

    val compositeContainerType: CompositeContainerType

}

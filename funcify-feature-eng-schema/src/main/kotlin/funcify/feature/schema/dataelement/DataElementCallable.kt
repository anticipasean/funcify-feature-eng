package funcify.feature.schema.dataelement

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.schema.path.operation.GQLOperationPath
import graphql.language.Field
import graphql.language.Value
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLFieldDefinition
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-08-01
 */
interface DataElementCallable : (ImmutableMap<GQLOperationPath, JsonNode>) -> Mono<JsonNode> {

    val domainSpecifiedDataElementSource: DomainSpecifiedDataElementSource

    val domainFieldCoordinates: FieldCoordinates
        get() = domainSpecifiedDataElementSource.domainFieldCoordinates

    val domainPath: GQLOperationPath
        get() = domainSpecifiedDataElementSource.domainPath

    val domainGraphQLFieldDefinition: GraphQLFieldDefinition
        get() = domainSpecifiedDataElementSource.domainFieldDefinition

    // val argumentsByPath: ImmutableMap<GQLOperationPath, GraphQLArgument>

    // val selectionsByPath: ImmutableMap<GQLOperationPath, GraphQLFieldDefinition>

    val selections: ImmutableSet<GQLOperationPath>

    /**
     * Takes argument values by path rather than by name as arguments vector may include those on
     * descendent fields on the domain field
     *
     * Example:
     * ```
     * query ($myDomainArgumentValue: ID, $myDomainChildField2ArgumentValue: String){
     *   dataElement {
     *     myDomainField(myDomainArgument: $myDomainArgumentValue) {
     *       myDomainChildField1
     *       myDomainChildField2(myDomainArgument: $myDomainChildField2ArgumentValue) {
     *         myDomainGrandChildField1
     *       }
     *     }
     *   }
     * }
     *
     * arguments.keys expected:
     *   - "gqlo:/dataElement/myDomainField?myDomainArgument"
     *   - "gqlo:/dataElement/myDomainField/myDomainChildField2?myDomainArgument"
     * ```
     *
     * Both arguments share the same _name_, but do not share the same path and may not share the
     * same value
     */
    override fun invoke(arguments: ImmutableMap<GQLOperationPath, JsonNode>): Mono<JsonNode>

    interface Builder {

        fun selectDomain(
            domainSpecifiedDataElementSource: DomainSpecifiedDataElementSource
        ): Builder

        fun selectPathWithinDomain(path: GQLOperationPath): Builder

        fun selectDirectivePathWithValueWithinDomain(
            path: GQLOperationPath,
            value: Value<*>
        ): Builder

        fun selectAllPathsWithinDomain(selections: Iterable<GQLOperationPath>): Builder

        fun build(): DataElementCallable
    }
}

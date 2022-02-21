package funcify.feature.schema

import funcify.feature.schema.datasource.DataSourceType
import funcify.feature.schema.graphql.GraphQLIndex


/**
 *
 * @author smccarron
 * @created 2/7/22
 */
interface CompositeIndex {

    fun canBeSourcedFrom(sourceType: DataSourceType): Boolean

    val graphQLIndex: GraphQLIndex

}
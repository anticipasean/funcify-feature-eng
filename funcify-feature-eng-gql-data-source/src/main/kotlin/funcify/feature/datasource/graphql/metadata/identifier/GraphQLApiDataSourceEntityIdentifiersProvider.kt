package funcify.feature.datasource.graphql.metadata.identifier

import funcify.feature.datasource.graphql.schema.GraphQLSourceIndex
import funcify.feature.schema.directive.entity.DataSourceEntityIdentifiersProvider

/**
 *
 * @author smccarron
 * @created 2022-09-16
 */
interface GraphQLApiDataSourceEntityIdentifiersProvider :
    DataSourceEntityIdentifiersProvider<GraphQLSourceIndex> {}

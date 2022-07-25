package funcify.feature.datasource.graphql.metadata.temporal

import funcify.feature.datasource.graphql.schema.GraphQLSourceIndex
import funcify.feature.schema.directive.temporal.DataSourceAttributeLastUpdatedProvider

/**
 *
 * @author smccarron
 * @created 2022-07-24
 */
interface GraphQLApiDataSourceLastUpdatedAttributeProvider : DataSourceAttributeLastUpdatedProvider<GraphQLSourceIndex> {


    }

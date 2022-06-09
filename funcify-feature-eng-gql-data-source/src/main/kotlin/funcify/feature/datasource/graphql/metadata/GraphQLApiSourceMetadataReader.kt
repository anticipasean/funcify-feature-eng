package funcify.feature.datasource.graphql.metadata

import funcify.feature.datasource.graphql.schema.GraphQLSourceIndex
import funcify.feature.datasource.metadata.reader.DataSourceMetadataReader
import graphql.schema.GraphQLSchema

interface GraphQLApiSourceMetadataReader :
    DataSourceMetadataReader<GraphQLSchema, GraphQLSourceIndex>

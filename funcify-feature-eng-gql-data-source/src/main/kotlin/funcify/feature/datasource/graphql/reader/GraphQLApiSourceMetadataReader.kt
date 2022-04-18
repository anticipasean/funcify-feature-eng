package funcify.feature.datasource.graphql.reader

import funcify.feature.datasource.graphql.schema.GraphQLSourceIndex
import funcify.feature.schema.datasource.reader.MetadataReader
import graphql.schema.GraphQLSchema

interface GraphQLApiSourceMetadataReader : MetadataReader<GraphQLSchema, GraphQLSourceIndex> {}

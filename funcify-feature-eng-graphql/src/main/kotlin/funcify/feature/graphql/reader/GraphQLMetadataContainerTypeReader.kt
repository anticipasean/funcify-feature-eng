package funcify.feature.graphql.reader

import funcify.feature.schema.datasource.SourceContainerType
import funcify.feature.schema.reader.MetadataContainerTypeReader
import graphql.schema.GraphQLSchema
import org.slf4j.Logger
import org.slf4j.LoggerFactory


/**
 *
 * @author smccarron
 * @created 4/8/22
 */
class GraphQLMetadataContainerTypeReader : MetadataContainerTypeReader<GraphQLSchema> {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(GraphQLMetadataContainerTypeReader::class.java)

    }

    override fun readSourceContainerTypesFromMetadata(input: GraphQLSchema): Iterable<SourceContainerType<*>> {
        logger.info("read_source_container_types_from_metadata: [ input.query_type.name: ${input.queryType.name} ]")
        TODO("Not yet implemented")
    }
}
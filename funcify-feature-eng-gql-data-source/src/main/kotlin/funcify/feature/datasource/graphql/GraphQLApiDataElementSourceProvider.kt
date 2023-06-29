package funcify.feature.datasource.graphql

import funcify.feature.schema.dataelementsource.DataElementSourceProvider
import funcify.feature.schema.dataelementsource.RawSourceType
import funcify.feature.schema.dataelementsource.SourceType
import org.springframework.core.io.ClassPathResource
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-06-29
 */
interface GraphQLApiDataElementSourceProvider :
    DataElementSourceProvider<GraphQLApiDataElementSource> {

    override val name: String

    override val sourceType: SourceType
    get() = RawSourceType.GRAPHQL_API

    override fun getLatestDataElementSource(): Mono<GraphQLApiDataElementSource>

    interface Builder {

        fun name(name: String): Builder

        fun graphQLApiService(service: GraphQLApiService): Builder

        fun graphQLSchemaClasspathResource(schemaClassPathResource: ClassPathResource): Builder

        fun build(): GraphQLApiDataElementSourceProvider

    }

}

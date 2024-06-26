package funcify.feature.datasource.graphql

import funcify.feature.schema.dataelement.DataElementSourceProvider
import funcify.feature.schema.sdl.transformer.TypeDefinitionRegistryTransformer
import funcify.feature.tools.container.attempt.Try
import org.springframework.core.io.ClassPathResource
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-06-29
 */
interface GraphQLApiDataElementSourceProvider :
    DataElementSourceProvider<GraphQLApiDataElementSource> {

    override val name: String

    override fun getLatestSource(): Mono<out GraphQLApiDataElementSource>

    interface Builder {

        fun name(name: String): Builder

        fun graphQLApiService(service: GraphQLApiService): Builder

        fun graphQLSchemaClasspathResource(schemaClassPathResource: ClassPathResource): Builder

        fun addTypeDefinitionRegistryTransformer(
            typeDefinitionRegistryTransformer: TypeDefinitionRegistryTransformer
        ): Builder

        fun build(): Try<GraphQLApiDataElementSourceProvider>
    }
}

package funcify.feature.file.metadata

import funcify.feature.error.ServiceError
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.MonoExtensions.widen
import funcify.feature.tools.extensions.StringExtensions.flatten
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import graphql.schema.idl.errors.SchemaProblem
import org.slf4j.Logger
import org.springframework.core.io.ClassPathResource
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-08-16
 */
internal class FeatureGraphQLSchemaClasspathResourceMetadataProvider :
    FileRegistryMetadataProvider<ClassPathResource> {

    companion object {
        private const val METHOD_TAG: String = "provide_type_definition_registry"
        private const val GRAPHQL_SCHEMA_FILE_EXTENSION = ".graphqls"
        private val logger: Logger =
            loggerFor<FeatureGraphQLSchemaClasspathResourceMetadataProvider>()
    }

    override fun provideTypeDefinitionRegistry(
        resource: ClassPathResource
    ): Mono<out TypeDefinitionRegistry> {
        logger.debug("{}: [ class_path_resource.path: {} ]", METHOD_TAG, resource.path)
        return Try.success(resource)
            .filter(ClassPathResource::exists) { cr: ClassPathResource ->
                ServiceError.of(
                    "schema file class_path_resource does not exist: [ path: %s ]",
                    cr.path
                )
            }
            .filter(
                { cr: ClassPathResource -> cr.path.endsWith(GRAPHQL_SCHEMA_FILE_EXTENSION) },
                { cr: ClassPathResource ->
                    ServiceError.of(
                        """schema file class_path_resource does 
                           |not end in expected graphql schema file extension: 
                           |[ expected: path.endsWith("$GRAPHQL_SCHEMA_FILE_EXTENSION"), 
                           |actual: ${cr.path} ]"""
                            .flatten()
                    )
                }
            )
            .flatMap { cr: ClassPathResource ->
                try {
                    Try.success(SchemaParser().parse(cr.inputStream))
                } catch (s: SchemaProblem) {
                    Try.failure<TypeDefinitionRegistry>(
                        ServiceError.builder()
                            .message(
                                """error occurred when parsing schema class_path_resource: 
                                    |[ type: %s, message: %s ]"""
                                    .flatten(),
                                s::class.qualifiedName,
                                s.message
                            )
                            .cause(s)
                            .build()
                    )
                }
            }
            .toMono()
            .doOnError { t: Throwable ->
                logger.error(
                    "{}: [ status: failed ][ type: {}, message: {} ]",
                    METHOD_TAG,
                    t::class.qualifiedName,
                    t.message
                )
            }
    }
}

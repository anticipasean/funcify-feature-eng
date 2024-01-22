package funcify.feature.datasource.graphql.metadata.transformer

import arrow.core.getOrElse
import funcify.feature.error.ServiceError
import funcify.feature.schema.sdl.transformer.OrderedTypeDefinitionRegistryTransformer
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.OptionExtensions.toOption
import graphql.GraphQLError
import graphql.language.FieldDefinition
import graphql.language.ObjectTypeDefinition
import graphql.schema.idl.TypeDefinitionRegistry
import graphql.schema.idl.TypeUtil
import org.slf4j.Logger
import org.springframework.core.Ordered
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-06-29
 */
class InternalQueryExcludingTypeDefinitionRegistryTransformer() :
    OrderedTypeDefinitionRegistryTransformer {

    companion object {
        private const val QUERY_OBJECT_TYPE_NAME = "Query"
        private val logger: Logger =
            loggerFor<InternalQueryExcludingTypeDefinitionRegistryTransformer>()
    }

    override fun getOrder(): Int {
        return Ordered.LOWEST_PRECEDENCE
    }

    override fun transform(
        typeDefinitionRegistry: TypeDefinitionRegistry
    ): Mono<out TypeDefinitionRegistry> {
        if (logger.isDebugEnabled) {
            logger.debug(
                "transform: [ type_definition_registry.get_type({}, {}): {} ]",
                QUERY_OBJECT_TYPE_NAME,
                ObjectTypeDefinition::class.java.name,
                typeDefinitionRegistry
                    .getType(QUERY_OBJECT_TYPE_NAME, ObjectTypeDefinition::class.java)
                    .toOption()
                    .map(ObjectTypeDefinition::getFieldDefinitions)
                    .map(List<FieldDefinition>::asSequence)
                    .getOrElse(::emptySequence)
                    .map { fd: FieldDefinition -> fd.name + ":" + TypeUtil.simplePrint(fd.type) }
                    .joinToString(", ", "[ ", " ]")
            )
        }
        return typeDefinitionRegistry
            .getType(QUERY_OBJECT_TYPE_NAME, ObjectTypeDefinition::class.java)
            .flatMap { otd: ObjectTypeDefinition ->
                val updatedDef: ObjectTypeDefinition =
                    otd.transform { otdb: ObjectTypeDefinition.Builder ->
                        otdb.fieldDefinitions(
                            otd.fieldDefinitions
                                .asSequence()
                                .filter { fd: FieldDefinition -> !fd.name.startsWith("_") }
                                .toList()
                        )
                    }
                typeDefinitionRegistry.remove(otd)
                typeDefinitionRegistry
                    .add(updatedDef)
                    .map { e: GraphQLError ->
                        ServiceError.builder()
                            .message(
                                "graphql_error occurred when excluding internal queries: %s",
                                e.toSpecification()
                            )
                            .build()
                    }
                    .map { t: Throwable -> Try.failure<TypeDefinitionRegistry>(t) }
            }
            .orElseGet { Try.success(typeDefinitionRegistry) }
            .toMono()
    }
}

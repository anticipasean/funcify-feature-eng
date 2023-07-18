package funcify.feature.datasource.graphql.metadata.filter

import funcify.feature.error.ServiceError
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import graphql.GraphQLError
import graphql.language.FieldDefinition
import graphql.language.ObjectTypeDefinition
import graphql.schema.idl.TypeDefinitionRegistry
import org.slf4j.Logger

/**
 * @author smccarron
 * @created 2023-06-29
 */
class InternalQueryExcludingTypeDefinitionRegistryFilter() : TypeDefinitionRegistryFilter {

    companion object {
        private const val QUERY_OBJECT_TYPE_NAME = "Query"
        private val logger: Logger = loggerFor<InternalQueryExcludingTypeDefinitionRegistryFilter>()
    }

    override fun filter(
        typeDefinitionRegistry: TypeDefinitionRegistry
    ): Result<TypeDefinitionRegistry> {
        if (logger.isDebugEnabled) {
            logger.debug(
                "filter: [ type_definition_registry.get_type({}, {}): {} ]",
                QUERY_OBJECT_TYPE_NAME,
                ObjectTypeDefinition::class.java.name,
                typeDefinitionRegistry.getType(
                    QUERY_OBJECT_TYPE_NAME,
                    ObjectTypeDefinition::class.java
                )
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
                            .message("graphql_error: %s", e.toSpecification())
                            .build()
                    }
                    .map { t: Throwable -> Result.failure<TypeDefinitionRegistry>(t) }
            }
            .orElseGet { Result.success(typeDefinitionRegistry) }
    }
}
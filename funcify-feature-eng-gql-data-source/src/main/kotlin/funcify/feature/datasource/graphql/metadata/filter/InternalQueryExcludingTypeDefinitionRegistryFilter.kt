package funcify.feature.datasource.graphql.metadata.filter

import funcify.feature.error.ServiceError
import graphql.GraphQLError
import graphql.language.FieldDefinition
import graphql.language.ObjectTypeDefinition
import graphql.schema.idl.TypeDefinitionRegistry

/**
 * @author smccarron
 * @created 2023-06-29
 */
class InternalQueryExcludingTypeDefinitionRegistryFilter : TypeDefinitionRegistryFilter {

    override fun filter(
        typeDefinitionRegistry: TypeDefinitionRegistry
    ): Result<TypeDefinitionRegistry> {
        return typeDefinitionRegistry
            .getType("Query", ObjectTypeDefinition::class.java)
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

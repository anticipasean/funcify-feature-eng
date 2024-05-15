package funcify.feature.materializer.schema

import funcify.feature.error.ServiceError
import funcify.feature.materializer.wiring.MaterializationGraphQLWiringFactory
import funcify.feature.schema.FeatureEngineeringModel
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import graphql.language.FieldDefinition
import graphql.language.ObjectTypeDefinition
import graphql.language.TypeName
import graphql.schema.GraphQLSchema
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.TypeDefinitionRegistry
import org.slf4j.Logger

internal class DefaultMaterializationGraphQLSchemaFactory(
    private val materializationGraphQLWiringFactory: MaterializationGraphQLWiringFactory
) : MaterializationGraphQLSchemaFactory {

    companion object {
        private val logger: Logger = loggerFor<DefaultMaterializationGraphQLSchemaFactory>()
        private const val QUERY_OBJECT_TYPE_NAME: String = "Query"
    }

    override fun createGraphQLSchemaFromMetamodel(
        featureEngineeringModel: FeatureEngineeringModel
    ): Try<GraphQLSchema> {
        val tdr: TypeDefinitionRegistry =
            TypeDefinitionRegistry().apply { addAll(featureEngineeringModel.modelDefinitions) }
        logger.info(
            """create_graphql_schema_from_metamodel: [ Query.field_definitions.name: {} ]"""
                .flatten(),
            tdr.getType(
                    TypeName.newTypeName(QUERY_OBJECT_TYPE_NAME).build(),
                    ObjectTypeDefinition::class.java
                )
                .orElse(null)
                ?.fieldDefinitions
                ?.asSequence()
                ?.map(FieldDefinition::getName)
                ?.joinToString(", ")
        )
        return Try.attempt {
                SchemaGenerator()
                    .makeExecutableSchema(
                        tdr,
                        RuntimeWiring.newRuntimeWiring()
                            .wiringFactory(materializationGraphQLWiringFactory)
                            .build()
                    )
            }
            .mapFailure { t: Throwable ->
                ServiceError.builder()
                    .message("error occurred when creating executable schema")
                    .cause(t)
                    .build()
            }
    }
}

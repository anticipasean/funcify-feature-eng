package funcify.feature.directive.wiring

import funcify.feature.util.StringExtensions.flatten
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLInputObjectField
import graphql.schema.idl.SchemaDirectiveWiring
import graphql.schema.idl.SchemaDirectiveWiringEnvironment
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class LastUpdatedMaterializationDirectiveWiring : SchemaDirectiveWiring {

    companion object {
        private val logger: Logger =
            LoggerFactory.getLogger(LastUpdatedMaterializationDirectiveWiring::class.java)
    }

    override fun onField(
        environment: SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition>
    ): GraphQLFieldDefinition {
        logger.debug(
            """on_field: [ env.element.name: ${environment.element.name}, 
              |env.directive.name: ${environment.directive.name} ]
              |""".flatten()
        )
        return environment.element
    }

    override fun onInputObjectField(
        environment: SchemaDirectiveWiringEnvironment<GraphQLInputObjectField>
    ): GraphQLInputObjectField {
        logger.debug(
            """on_input_object_field: [ env.element.name: ${environment.element.name}, 
               |env.directive.name: ${environment.directive.name} ]
               |""".flatten()
        )
        return environment.element
    }
}

package funcify.feature.datasource.sdl

import funcify.feature.schema.datasource.SourceAttribute
import funcify.feature.tools.container.attempt.Try
import graphql.language.FieldDefinition

interface SourceAttributeGqlSdlFieldDefinitionMapper<SA : SourceAttribute> {

    fun convertSourceAttributeIntoGraphQLFieldSDLDefinition(
        sourceAttribute: SA
    ): Try<FieldDefinition>
}

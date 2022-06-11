package funcify.feature.datasource.gql

import funcify.feature.schema.datasource.SourceContainerType
import funcify.feature.tools.container.attempt.Try
import graphql.language.ObjectTypeDefinition

interface SourceContainerTypeGqlSdlObjectTypeDefinitionMapper<SC : SourceContainerType<*>> {

    fun convertSourceContainerTypeIntoGraphQLObjectTypeSDLDefinition(
        sourceContainerType: SC
    ): Try<ObjectTypeDefinition>
}

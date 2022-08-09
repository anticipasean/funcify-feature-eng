package funcify.feature.datasource.graphql.naming

import funcify.feature.datasource.graphql.naming.GraphQLSourceNamingConventions.ConventionType.FIELD_NAMING_CONVENTION
import funcify.feature.naming.NamingConvention
import funcify.feature.naming.NamingConventionFactory
import funcify.feature.naming.StandardNamingConventions
import graphql.schema.GraphQLFieldDefinition

/**
 *
 * @author smccarron
 * @created 4/9/22
 */
object GraphQLSourceNamingConventions {

    enum class ConventionType {
        FIELD_NAMING_CONVENTION
    }

    private val FIELD_DEFINITION_NAMING_CONVENTION:
        NamingConvention<GraphQLFieldDefinition> by lazy {
        NamingConventionFactory.getDefaultFactory()
            .createConventionFrom(StandardNamingConventions.CAMEL_CASE)
            .mapping<GraphQLFieldDefinition> { fd -> fd.name }
            .namedAndIdentifiedBy("GraphQLFieldDefinitionName", FIELD_NAMING_CONVENTION)
    }

    fun getFieldNamingConventionForGraphQLFieldDefinitions():
        NamingConvention<GraphQLFieldDefinition> {
        return FIELD_DEFINITION_NAMING_CONVENTION
    }
}

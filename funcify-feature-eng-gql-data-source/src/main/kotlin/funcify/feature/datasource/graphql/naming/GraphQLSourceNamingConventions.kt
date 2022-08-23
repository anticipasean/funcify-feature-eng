package funcify.feature.datasource.graphql.naming

import funcify.feature.naming.NamingConvention
import funcify.feature.naming.NamingConventionFactory
import funcify.feature.naming.StandardNamingConventions
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLFieldsContainer
import graphql.schema.GraphQLInputFieldsContainer

/**
 *
 * @author smccarron
 * @created 4/9/22
 */
object GraphQLSourceNamingConventions {

    enum class ConventionType {
        FIELD_NAMING_CONVENTION_TYPE,
        OUTPUT_TYPE_NAMING_CONVENTION_TYPE,
        INPUT_TYPE_NAMING_CONVENTION_TYPE
    }

    private val FIELD_DEFINITION_NAMING_CONVENTION:
        NamingConvention<GraphQLFieldDefinition> by lazy {
        NamingConventionFactory.getDefaultFactory()
            .createConventionFrom(StandardNamingConventions.CAMEL_CASE)
            .mapping<GraphQLFieldDefinition> { fd -> fd.name }
            .namedAndIdentifiedBy(
                "GraphQLFieldDefinitionName",
                ConventionType.FIELD_NAMING_CONVENTION_TYPE
            )
    }

    private val OUTPUT_TYPE_NAMING_CONVENTION: NamingConvention<GraphQLFieldsContainer> by lazy {
        NamingConventionFactory.getDefaultFactory()
            .createConventionFrom(StandardNamingConventions.PASCAL_CASE)
            .mapping<GraphQLFieldsContainer> { ofc -> ofc.name }
            .namedAndIdentifiedBy(
                "GraphQLOutputFieldContainerName",
                ConventionType.OUTPUT_TYPE_NAMING_CONVENTION_TYPE
            )
    }

    private val INPUT_TYPE_NAMING_CONVENTION:
        NamingConvention<GraphQLInputFieldsContainer> by lazy {
        NamingConventionFactory.getDefaultFactory()
            .createConventionFrom(StandardNamingConventions.PASCAL_CASE)
            .mapping<GraphQLInputFieldsContainer> { ifc -> ifc.name }
            .namedAndIdentifiedBy(
                "GraphQLInputFieldContainerName",
                ConventionType.INPUT_TYPE_NAMING_CONVENTION_TYPE
            )
    }

    fun getFieldNamingConventionForGraphQLFieldDefinitions():
        NamingConvention<GraphQLFieldDefinition> {
        return FIELD_DEFINITION_NAMING_CONVENTION
    }

    fun getOutputTypeNamingConventionForGraphQLFieldsContainers():
        NamingConvention<GraphQLFieldsContainer> {
        return OUTPUT_TYPE_NAMING_CONVENTION
    }

    fun getInputTypeNamingConventionForGraphQLInputFieldsContainers():
        NamingConvention<GraphQLInputFieldsContainer> {
        return INPUT_TYPE_NAMING_CONVENTION
    }
}

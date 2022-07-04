package funcify.feature.datasource.graphql.schema

import funcify.feature.datasource.graphql.metadata.GraphQLApiSourceMetadataFilter
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.attempt.Try
import graphql.schema.GraphQLAppliedDirective
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLObjectType
import kotlinx.collections.immutable.ImmutableSet

/**
 *
 * @author smccarron
 * @created 4/10/22
 */
interface GraphQLSourceIndexFactory {

    fun createRootSourceContainerTypeForDataSourceKey(
        key: DataSource.Key<GraphQLSourceIndex>
    ): RootSourceContainerTypeSpec

    fun createSourceContainerTypeForDataSourceKey(
        key: DataSource.Key<GraphQLSourceIndex>
    ): AttributeBase

    fun updateSourceContainerType(
        graphQLSourceContainerType: GraphQLSourceContainerType
    ): SourceContainerTypeUpdateSpec

    fun createSourceAttributeForDataSourceKey(
        key: DataSource.Key<GraphQLSourceIndex>
    ): SourceParentDefinitionBase

    fun createParameterContainerTypeForDataSourceKey(
        key: DataSource.Key<GraphQLSourceIndex>
    ): ParameterContainerTypeBase

    fun updateParameterContainerType(
        graphQLParameterContainerType: GraphQLParameterContainerType
    ): ParameterContainerTypeUpdateSpec

    fun createParameterAttributeForDataSourceKey(
        key: DataSource.Key<GraphQLSourceIndex>
    ): ParameterParentDefinitionBase

    interface RootSourceContainerTypeSpec {
        fun forGraphQLQueryObjectType(
            queryObjectType: GraphQLObjectType,
            metadataFilter: GraphQLApiSourceMetadataFilter =
                GraphQLApiSourceMetadataFilter.INCLUDE_ALL_FILTER
        ): Try<GraphQLSourceContainerType>
    }

    interface AttributeBase {

        fun forAttributePathAndDefinition(
            attributePath: SchematicPath,
            attributeDefinition: GraphQLFieldDefinition
        ): Try<GraphQLSourceContainerType>
    }

    interface SourceParentDefinitionBase {

        fun withParentPathAndDefinition(
            parentPath: SchematicPath,
            parentDefinition: GraphQLFieldDefinition
        ): ChildAttributeSpec
    }

    interface ChildAttributeSpec {

        fun forChildAttributeDefinition(
            childDefinition: GraphQLFieldDefinition
        ): Try<GraphQLSourceAttribute>
    }

    interface SourceContainerTypeUpdateSpec {

        fun withChildSourceAttributes(
            graphQLSourceAttributes: ImmutableSet<GraphQLSourceAttribute>
        ): Try<GraphQLSourceContainerType>
    }

    interface ParameterContainerTypeUpdateSpec {

        fun withChildSourceAttributes(
            graphQLParameterAttributes: ImmutableSet<GraphQLParameterAttribute>
        ): Try<GraphQLParameterContainerType>
    }

    interface ParameterParentDefinitionBase {

        fun withParentPathAndDefinition(
            parentPath: SchematicPath,
            parentDefinition: GraphQLFieldDefinition
        ): ParameterAttributeSpec
    }

    interface ParameterAttributeSpec {

        fun forChildArgument(childArgument: GraphQLArgument): Try<GraphQLParameterAttribute>

        fun forChildDirective(
            childDirective: GraphQLAppliedDirective
        ): Try<GraphQLParameterAttribute>
    }

    interface ParameterContainerTypeBase {

        fun forArgumentNameAndInputObjectType(
            name: String,
            inputObjectType: GraphQLInputObjectType
        ): InputObjectTypeContainerSpec

        fun forDirective(directive: GraphQLAppliedDirective): DirectiveContainerTypeSpec
    }

    interface InputObjectTypeContainerSpec {

        fun onFieldArgumentValue(
            argumentPath: SchematicPath,
            graphQLArgument: GraphQLArgument
        ): Try<GraphQLParameterContainerType>

        fun onDirectiveArgumentValue(
            directivePath: SchematicPath,
            graphQLAppliedDirective: GraphQLAppliedDirective
        ): Try<GraphQLParameterContainerType>
    }

    interface DirectiveContainerTypeSpec {

        fun onFieldDefinition(
            sourceAttributePath: SchematicPath,
            graphQLFieldDefinition: GraphQLFieldDefinition
        ): Try<GraphQLParameterContainerType>
    }
}

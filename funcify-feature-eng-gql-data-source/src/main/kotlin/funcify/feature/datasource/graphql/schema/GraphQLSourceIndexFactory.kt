package funcify.feature.datasource.graphql.schema

import funcify.feature.schema.datasource.DataElementSource
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.attempt.Try
import graphql.schema.GraphQLAppliedDirective
import graphql.schema.GraphQLAppliedDirectiveArgument
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLInputObjectField
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLNamedSchemaElement
import graphql.schema.GraphQLObjectType
import kotlinx.collections.immutable.ImmutableSet

/**
 *
 * @author smccarron
 * @created 4/10/22
 */
interface GraphQLSourceIndexFactory {

    fun createRootSourceContainerTypeForDataSourceKey(
        key: DataElementSource.Key<GraphQLSourceIndex>
    ): RootSourceContainerTypeSpec

    fun createSourceContainerTypeForDataSourceKey(
        key: DataElementSource.Key<GraphQLSourceIndex>
    ): AttributeBase

    fun updateSourceContainerType(
        graphQLSourceContainerType: GraphQLSourceContainerType
    ): SourceContainerTypeUpdateSpec

    fun createSourceAttributeForDataSourceKey(
        key: DataElementSource.Key<GraphQLSourceIndex>
    ): SourceParentDefinitionBase

    fun createParameterContainerTypeForDataSourceKey(
        key: DataElementSource.Key<GraphQLSourceIndex>
    ): ParameterContainerTypeBase

    fun createParameterContainerTypeForParameterAttributeWithInputObjectValue(
        parameterAttribute: GraphQLParameterAttribute
    ): Try<GraphQLParameterContainerType>

    fun updateParameterContainerType(
        graphQLParameterContainerType: GraphQLParameterContainerType
    ): ParameterContainerTypeUpdateSpec

    fun createParameterAttributeForDataSourceKey(
        key: DataElementSource.Key<GraphQLSourceIndex>
    ): ParameterParentDefinitionBase

    interface RootSourceContainerTypeSpec {
        fun forGraphQLQueryObjectType(
            queryObjectType: GraphQLObjectType
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

        fun withParentRootContainerType(queryRootObjectType: GraphQLObjectType): ChildAttributeSpec
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

        fun withChildParameterAttributes(
            graphQLParameterAttributes: ImmutableSet<GraphQLParameterAttribute>
        ): Try<GraphQLParameterContainerType>
    }

    interface ParameterParentDefinitionBase {

        fun forAppliedDirective(
            appliedDirective: GraphQLAppliedDirective
        ): AppliedDirectiveAttributeSpec

        fun withParentPathAndFieldDefinition(
            parentPath: SchematicPath,
            parentDefinition: GraphQLFieldDefinition
        ): ParameterAttributeSpec

        fun withParentPathAndAppliedDirective(
            parentPath: SchematicPath,
            parentAppliedDirective: GraphQLAppliedDirective
        ): ParameterDirectiveArgumentAttributeSpec

        fun withParentPathAndFieldArgument(
            parentPath: SchematicPath,
            parentArgument: GraphQLArgument
        ): ParameterAttributeInputObjectFieldSpec

        fun withParentPathAndDirectiveArgument(
            parentPath: SchematicPath,
            parentDirectiveArgument: GraphQLAppliedDirectiveArgument
        ): ParameterAttributeInputObjectFieldSpec

        fun withParentPathAndInputObjectType(
            parentPath: SchematicPath,
            parentInputObjectType: GraphQLInputObjectType
        ): ParameterAttributeInputObjectFieldSpec
    }

    interface ParameterAttributeSpec {

        fun forChildArgument(childArgument: GraphQLArgument): Try<GraphQLParameterAttribute>
    }

    interface ParameterDirectiveArgumentAttributeSpec {

        fun forChildDirectiveArgument(
            childDirectiveArgument: GraphQLAppliedDirectiveArgument
        ): Try<GraphQLParameterAttribute>
    }

    interface ParameterAttributeInputObjectFieldSpec {

        fun forInputObjectField(
            childInputObjectField: GraphQLInputObjectField
        ): Try<GraphQLParameterAttribute>
    }

    interface ParameterContainerTypeBase {

        fun forFieldArgument(fieldArgument: GraphQLArgument): InputObjectTypeContainerSpec

        fun forAppliedDirective(directive: GraphQLAppliedDirective): AppliedDirectiveContainerSpec

        fun forDirectiveArgument(
            directiveArgument: GraphQLAppliedDirectiveArgument
        ): DirectiveContainerTypeSpec
    }

    interface InputObjectTypeContainerSpec {

        fun onFieldDefinition(
            parentFieldDefinitionPath: SchematicPath,
            parentFieldDefinition: GraphQLFieldDefinition
        ): Try<GraphQLParameterContainerType>
    }

    interface DirectiveContainerTypeSpec {

        fun onParentDirective(
            parentDirectivePath: SchematicPath,
            parentAppliedDirective: GraphQLAppliedDirective
        ): Try<GraphQLParameterContainerType>
    }

    interface AppliedDirectiveContainerSpec {

        fun onParentDefinition(
            parentPath: SchematicPath,
            parentSchemaElement: GraphQLNamedSchemaElement
        ): Try<GraphQLParameterContainerType>
    }

    interface AppliedDirectiveAttributeSpec {

        fun onParentDefinition(
            parentPath: SchematicPath,
            parentSchemaElement: GraphQLNamedSchemaElement
        ): Try<GraphQLParameterAttribute>
    }
}

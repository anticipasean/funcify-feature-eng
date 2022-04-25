package funcify.feature.datasource.graphql.schema

import arrow.core.Option
import funcify.feature.schema.path.SchematicPath
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLObjectType
import kotlinx.collections.immutable.ImmutableSet

/**
 *
 * @author smccarron
 * @created 4/10/22
 */
interface GraphQLSourceIndexFactory {

    companion object {

        fun createRootSourceContainerType(): RootSourceContainerTypeSpec {
            return DefaultGraphQLSourceIndexFactory.DefaultRootContainerTypeSpec()
        }

        fun createSourceContainerType(): AttributeBase {
            return DefaultGraphQLSourceIndexFactory.DefaultAttributeBase()
        }

        fun updateSourceContainerType(
            graphQLSourceContainerType: GraphQLSourceContainerType
        ): SourceContainerTypeUpdateSpec {
            return DefaultGraphQLSourceIndexFactory.DefaultSourceContainerTypeUpdateSpec(
                graphQLSourceContainerType
            )
        }

        fun createSourceAttribute(): ParentDefinitionBase {
            return DefaultGraphQLSourceIndexFactory.DefaultParentDefinitionBase()
        }
    }

    interface RootSourceContainerTypeSpec {
        fun forGraphQLQueryObjectType(
            queryObjectType: GraphQLObjectType
        ): GraphQLSourceContainerType
    }

    interface AttributeBase {

        fun forAttributePathAndDefinition(
            attributePath: SchematicPath,
            attributeDefinition: GraphQLFieldDefinition
        ): Option<GraphQLSourceContainerType>
    }

    interface ParentDefinitionBase {

        fun withParentPathAndDefinition(
            parentPath: SchematicPath,
            parentDefinition: GraphQLFieldDefinition
        ): ChildAttributeSpec
    }

    interface ChildAttributeSpec {

        fun forChildAttributeDefinition(
            childDefinition: GraphQLFieldDefinition
        ): GraphQLSourceAttribute
    }

    interface SourceContainerTypeUpdateSpec {
        fun withChildSourceAttributes(
            graphQLSourceAttributes: ImmutableSet<GraphQLSourceAttribute>
        ): GraphQLSourceContainerType
    }
}

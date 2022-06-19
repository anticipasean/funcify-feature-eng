package funcify.feature.datasource.graphql.schema

import arrow.core.Option
import funcify.feature.datasource.graphql.metadata.GraphQLApiSourceMetadataFilter
import funcify.feature.schema.datasource.DataSource
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
    ): ParentDefinitionBase

    interface RootSourceContainerTypeSpec {
        fun forGraphQLQueryObjectType(
            queryObjectType: GraphQLObjectType,
            metadataFilter: GraphQLApiSourceMetadataFilter =
                GraphQLApiSourceMetadataFilter.INCLUDE_ALL_FILTER
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

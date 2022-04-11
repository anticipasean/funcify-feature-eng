package funcify.feature.datasource.graphql.schema

import arrow.core.Option
import funcify.feature.schema.path.SchematicPath
import graphql.schema.GraphQLFieldDefinition
import kotlinx.collections.immutable.ImmutableSet


/**
 *
 * @author smccarron
 * @created 4/10/22
 */
interface GraphQLSourceIndexFactory {

    companion object {

        fun createRootIndices(): RootBase {
            return DefaultGraphQLSourceIndexFactory.DefaultRootBase()
        }

        fun createSourceContainerType(): AttributeBase {
            return DefaultGraphQLSourceIndexFactory.DefaultAttributeBase()
        }

        fun createSourceAttribute(): ParentDefinitionBase {
            return DefaultGraphQLSourceIndexFactory.DefaultParentDefinitionBase()
        }

    }

    interface RootBase {

        fun fromRootDefinition(fieldDefinition: GraphQLFieldDefinition): ImmutableSet<GraphQLSourceIndex>

    }


    interface AttributeBase {

        fun forAttributePathAndDefinition(attributePath: SchematicPath,
                                          attributeDefinition: GraphQLFieldDefinition): Option<GraphQLSourceContainerType>
    }

    interface ParentDefinitionBase {

        fun withParentPathAndDefinition(parentPath: SchematicPath,
                                        parentDefinition: GraphQLFieldDefinition): ChildAttributeBuilder

    }

    interface ChildAttributeBuilder {

        fun forChildAttributeDefinition(childDefinition: GraphQLFieldDefinition): GraphQLSourceAttribute

    }


}
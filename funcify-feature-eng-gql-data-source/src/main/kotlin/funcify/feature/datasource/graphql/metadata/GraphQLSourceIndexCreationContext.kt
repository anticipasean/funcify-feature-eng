package funcify.feature.datasource.graphql.metadata

import arrow.core.Option
import arrow.core.firstOrNone
import arrow.core.identity
import arrow.core.toOption
import funcify.feature.datasource.graphql.schema.GraphQLParameterAttribute
import funcify.feature.datasource.graphql.schema.GraphQLParameterContainerType
import funcify.feature.datasource.graphql.schema.GraphQLSourceAttribute
import funcify.feature.datasource.graphql.schema.GraphQLSourceContainerType
import funcify.feature.datasource.graphql.schema.GraphQLSourceIndex
import funcify.feature.schema.path.SchematicPath
import graphql.schema.GraphQLAppliedDirective
import graphql.schema.GraphQLAppliedDirectiveArgument
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLInputObjectField
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchemaElement
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

/**
 *
 * @author smccarron
 * @created 2022-07-05
 */
sealed interface GraphQLSourceIndexCreationContext<E : GraphQLSchemaElement> {

    val schematicPathCreatedBySchemaElement: ImmutableMap<GraphQLSchemaElement, SchematicPath>

    val schemaElementsBySchematicPath:
        ImmutableMap<SchematicPath, ImmutableSet<GraphQLSchemaElement>>

    val graphqlSourceContainerTypesBySchematicPath:
        ImmutableMap<SchematicPath, GraphQLSourceContainerType>

    val graphqlSourceAttributesBySchematicPath: ImmutableMap<SchematicPath, GraphQLSourceAttribute>

    val graphqlParameterContainerTypesBySchematicPath:
        ImmutableMap<SchematicPath, GraphQLParameterContainerType>

    val graphqlParameterAttributesBySchematicPath:
        ImmutableMap<SchematicPath, GraphQLParameterAttribute>

    val parentPath: Option<SchematicPath>

    val parentElements: ImmutableSet<GraphQLSchemaElement>
        get() =
            parentPath
                .flatMap { pp -> schemaElementsBySchematicPath[pp].toOption() }
                .fold(::persistentSetOf, ::identity)

    val currentElement: E

    fun <NE : GraphQLSchemaElement> update(
        transformer: (Builder<E>) -> Builder<NE>
    ): GraphQLSourceIndexCreationContext<NE>

    interface Builder<E : GraphQLSchemaElement> {

        fun <SI : GraphQLSourceIndex> addOrUpdateGraphQLSourceIndex(
            graphQLSourceIndex: SI
        ): Builder<E>

        fun <NE : GraphQLSchemaElement> nextSchemaElement(
            parentPath: SchematicPath,
            nextElement: NE
        ): Builder<NE>

        fun build(): GraphQLSourceIndexCreationContext<E>
    }

    interface OutputObjectTypeSourceIndexCreationContext :
        GraphQLSourceIndexCreationContext<GraphQLObjectType> {

        val parentSourceContainerType: Option<GraphQLSourceContainerType>
            get() =
                parentPath.flatMap { pp ->
                    graphqlSourceContainerTypesBySchematicPath[pp].toOption()
                }

        val parentSourceAttribute: Option<GraphQLSourceAttribute>
            get() =
                parentPath.flatMap { pp -> graphqlSourceAttributesBySchematicPath[pp].toOption() }
    }

    interface FieldDefinitionSourceIndexCreationContext :
        GraphQLSourceIndexCreationContext<GraphQLFieldDefinition> {

        val parentSourceContainerType: Option<GraphQLSourceContainerType>
            get() =
                parentPath.flatMap { pp ->
                    graphqlSourceContainerTypesBySchematicPath[pp].toOption()
                }
    }

    interface ArgumentParameterSourceIndexCreationContext :
        GraphQLSourceIndexCreationContext<GraphQLArgument> {

        val parentSourceAttribute: Option<GraphQLSourceAttribute>
            get() =
                parentPath.flatMap { pp -> graphqlSourceAttributesBySchematicPath[pp].toOption() }

        val parentFieldDefinition: Option<GraphQLFieldDefinition>
            get() = parentElements.filterIsInstance<GraphQLFieldDefinition>().firstOrNone()
    }

    interface InputObjectFieldSourceIndexCreationContext :
        GraphQLSourceIndexCreationContext<GraphQLInputObjectField> {

        val parentParameterAttribute: Option<GraphQLParameterAttribute>
            get() =
                parentPath.flatMap { pp ->
                    graphqlParameterAttributesBySchematicPath[pp].toOption()
                }

        val parentInputObjectType: Option<GraphQLInputObjectType>
            get() = parentElements.filterIsInstance<GraphQLInputObjectType>().firstOrNone()
    }

    /** Can have different types of parent elements */
    interface DirectiveSourceIndexCreationContext :
        GraphQLSourceIndexCreationContext<GraphQLAppliedDirective> {

        val parentFieldDefinition: Option<GraphQLFieldDefinition>
            get() = parentElements.filterIsInstance<GraphQLFieldDefinition>().firstOrNone()

        val parentArgument: Option<GraphQLArgument>
            get() = parentElements.filterIsInstance<GraphQLArgument>().firstOrNone()

        val parentInputObjectType: Option<GraphQLInputObjectType>
            get() = parentElements.filterIsInstance<GraphQLInputObjectType>().firstOrNone()
    }

    interface DirectiveArgumentSourceIndexCreationContext :
        GraphQLSourceIndexCreationContext<GraphQLAppliedDirectiveArgument> {

        val parentParameterContainerType: Option<GraphQLParameterContainerType>
            get() =
                parentPath.flatMap { pp ->
                    graphqlParameterContainerTypesBySchematicPath[pp].toOption()
                }

        val parentDirective: Option<GraphQLAppliedDirective>
            get() = parentElements.filterIsInstance<GraphQLAppliedDirective>().firstOrNone()
    }
}

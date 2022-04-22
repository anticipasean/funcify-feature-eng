package funcify.feature.datasource.graphql.schema

import arrow.core.Option
import arrow.core.filterIsInstance
import arrow.core.getOrElse
import arrow.core.lastOrNone
import arrow.core.none
import arrow.core.or
import arrow.core.some
import arrow.core.toOption
import funcify.feature.datasource.graphql.error.GQLDataSourceErrorResponse
import funcify.feature.datasource.graphql.error.GQLDataSourceException
import funcify.feature.datasource.graphql.naming.GraphQLSourceNamingConventions
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.path.SchematicPathFactory
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLFieldsContainer
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNamedOutputType
import graphql.schema.GraphQLOutputType
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList

/**
 *
 * @author smccarron
 * @created 4/10/22
 */
internal class DefaultGraphQLSourceIndexFactory {

    class DefaultRootBase() : GraphQLSourceIndexFactory.RootBase {

        override fun fromRootDefinition(
            fieldDefinition: GraphQLFieldDefinition
        ): ImmutableSet<GraphQLSourceIndex> {
            val convPathName =
                GraphQLSourceNamingConventions.getPathNamingConventionForGraphQLFieldDefinitions()
                    .deriveName(fieldDefinition)
            val convFieldName =
                GraphQLSourceNamingConventions.getFieldNamingConventionForGraphQLFieldDefinitions()
                    .deriveName(fieldDefinition)
            val sourcePath: SchematicPath =
                SchematicPathFactory.createPathWithSegments(
                    pathSegments = persistentListOf(convPathName.qualifiedForm)
                )
            when (val fieldType: GraphQLOutputType = fieldDefinition.type) {
                is GraphQLFieldsContainer -> {
                    val graphQLSourceContainerType =
                        DefaultGraphQLSourceContainerType(
                            sourcePath = sourcePath,
                            name = convFieldName,
                            schemaFieldDefinition = fieldDefinition
                        )
                    val graphQLSourceAttribute =
                        DefaultGraphQLSourceAttribute(
                            sourcePath = sourcePath,
                            name = convFieldName,
                            schemaFieldDefinition = fieldDefinition
                        )
                    return persistentSetOf(graphQLSourceContainerType, graphQLSourceAttribute)
                }
                is GraphQLList -> {
                    if (fieldType.wrappedType is GraphQLFieldsContainer) {
                        val graphQLSourceContainerType =
                            DefaultGraphQLSourceContainerType(
                                sourcePath = sourcePath,
                                name = convFieldName,
                                schemaFieldDefinition = fieldDefinition
                            )
                        val graphQLSourceAttribute =
                            DefaultGraphQLSourceAttribute(
                                sourcePath = sourcePath,
                                name = convFieldName,
                                schemaFieldDefinition = fieldDefinition
                            )
                        return persistentSetOf(graphQLSourceContainerType, graphQLSourceAttribute)
                    } else {
                        val graphQLSourceAttribute =
                            DefaultGraphQLSourceAttribute(
                                sourcePath = sourcePath,
                                name = convFieldName,
                                schemaFieldDefinition = fieldDefinition
                            )
                        return persistentSetOf(graphQLSourceAttribute)
                    }
                }
                else -> {
                    val graphQLSourceAttribute =
                        DefaultGraphQLSourceAttribute(
                            sourcePath = sourcePath,
                            name = convFieldName,
                            schemaFieldDefinition = fieldDefinition
                        )
                    return persistentSetOf(graphQLSourceAttribute)
                }
            }
        }
    }

    class DefaultAttributeBase() : GraphQLSourceIndexFactory.AttributeBase {

        override fun forAttributePathAndDefinition(
            attributePath: SchematicPath,
            attributeDefinition: GraphQLFieldDefinition
        ): Option<GraphQLSourceContainerType> {
            when (val fieldType = attributeDefinition.type) {
                is GraphQLFieldsContainer -> {
                    return DefaultGraphQLSourceContainerType(
                            sourcePath = attributePath,
                            name =
                                GraphQLSourceNamingConventions
                                    .getFieldNamingConventionForGraphQLFieldDefinitions()
                                    .deriveName(attributeDefinition),
                            schemaFieldDefinition = attributeDefinition
                        )
                        .some()
                }
                is GraphQLList -> {
                    return if (fieldType.wrappedType is GraphQLFieldsContainer) {
                        DefaultGraphQLSourceContainerType(
                                sourcePath = attributePath,
                                name =
                                    GraphQLSourceNamingConventions
                                        .getFieldNamingConventionForGraphQLFieldDefinitions()
                                        .deriveName(attributeDefinition),
                                schemaFieldDefinition = attributeDefinition
                            )
                            .some()
                    } else {
                        none<DefaultGraphQLSourceContainerType>()
                    }
                }
                else -> {
                    return none<DefaultGraphQLSourceContainerType>()
                }
            }
        }
    }

    class DefaultParentDefinitionBase() : GraphQLSourceIndexFactory.ParentDefinitionBase {

        override fun withParentPathAndDefinition(
            parentPath: SchematicPath,
            parentDefinition: GraphQLFieldDefinition
        ): GraphQLSourceIndexFactory.ChildAttributeBuilder {
            return DefaultChildAttributeBuilder(
                parentPath = parentPath,
                parentDefinition = parentDefinition
            )
        }
    }

    class DefaultChildAttributeBuilder(
        private val parentPath: SchematicPath,
        private val parentDefinition: GraphQLFieldDefinition
    ) : GraphQLSourceIndexFactory.ChildAttributeBuilder {

        override fun forChildAttributeDefinition(
            childDefinition: GraphQLFieldDefinition
        ): GraphQLSourceAttribute {
            if (parentPath.pathSegments.isEmpty() ||
                    parentPath
                        .pathSegments
                        .lastOrNone()
                        .filter { s ->
                            s !=
                                GraphQLSourceNamingConventions
                                    .getPathNamingConventionForGraphQLFieldDefinitions()
                                    .deriveName(parentDefinition)
                                    .qualifiedForm
                        }
                        .isDefined()
            ) {
                val message =
                    """
                    |parent_path [ path: ${parentPath.toURI()} ] does not match expected requirements 
                    |for parent_definition input [ qualified_name: ${
                    GraphQLSourceNamingConventions.getPathNamingConventionForGraphQLFieldDefinitions()
                            .deriveName(parentDefinition).qualifiedForm
                }
                    |""".flattenIntoOneLine()
                throw IllegalArgumentException(message)
            }
            if (parentDefinition
                    .type
                    .toOption()
                    .filterIsInstance<GraphQLFieldsContainer>()
                    .or(
                        parentDefinition
                            .type
                            .toOption()
                            .filterIsInstance<GraphQLList>()
                            .map { gqll: GraphQLList -> gqll.wrappedType }
                            .filterIsInstance<GraphQLFieldsContainer>()
                    )
                    .filter { gqlObjType -> !gqlObjType.fieldDefinitions.contains(childDefinition) }
                    .isDefined()
            ) {
                val message =
                    """
                    |child_definition [ name: ${childDefinition.name} ] is not found in child_field_definitions 
                    |of parent_definition object_type or wrapped object_type [ name: ${
                    parentDefinition.type.toOption()
                            .filterIsInstance<GraphQLNamedOutputType>()
                            .map(GraphQLNamedOutputType::getName)
                            .getOrElse { "<NA>" }
                } ]
                    """.flattenIntoOneLine()
                throw GQLDataSourceException(GQLDataSourceErrorResponse.INVALID_INPUT, message)
            }
            val childConvPathName =
                GraphQLSourceNamingConventions.getPathNamingConventionForGraphQLFieldDefinitions()
                    .deriveName(childDefinition)
            val childConvFieldName =
                GraphQLSourceNamingConventions.getFieldNamingConventionForGraphQLFieldDefinitions()
                    .deriveName(childDefinition)
            val childPath =
                SchematicPathFactory.createPathWithSegments(
                    pathSegments =
                        parentPath
                            .pathSegments
                            .toPersistentList()
                            .add(childConvPathName.qualifiedForm)
                )
            return DefaultGraphQLSourceAttribute(
                sourcePath = childPath,
                name = childConvFieldName,
                schemaFieldDefinition = childDefinition
            )
        }
    }
}

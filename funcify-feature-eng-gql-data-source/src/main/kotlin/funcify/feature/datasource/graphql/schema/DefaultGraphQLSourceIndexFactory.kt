package funcify.feature.datasource.graphql.schema

import arrow.core.Option
import arrow.core.Some
import arrow.core.filterIsInstance
import arrow.core.getOrElse
import arrow.core.lastOrNone
import arrow.core.none
import arrow.core.some
import arrow.core.toOption
import funcify.feature.datasource.graphql.error.GQLDataSourceErrorResponse
import funcify.feature.datasource.graphql.error.GQLDataSourceException
import funcify.feature.datasource.graphql.metadata.GraphQLApiSourceMetadataFilter
import funcify.feature.datasource.graphql.naming.GraphQLSourceNamingConventions
import funcify.feature.datasource.graphql.schema.GraphQLSourceIndexFactory.AttributeBase
import funcify.feature.datasource.graphql.schema.GraphQLSourceIndexFactory.ChildAttributeSpec
import funcify.feature.datasource.graphql.schema.GraphQLSourceIndexFactory.ParameterAttributeSpec
import funcify.feature.datasource.graphql.schema.GraphQLSourceIndexFactory.ParameterContainerTypeBase
import funcify.feature.datasource.graphql.schema.GraphQLSourceIndexFactory.ParameterContainerTypeUpdateSpec
import funcify.feature.datasource.graphql.schema.GraphQLSourceIndexFactory.ParameterParentDefinitionBase
import funcify.feature.datasource.graphql.schema.GraphQLSourceIndexFactory.RootSourceContainerTypeSpec
import funcify.feature.datasource.graphql.schema.GraphQLSourceIndexFactory.SourceContainerTypeUpdateSpec
import funcify.feature.datasource.graphql.schema.GraphQLSourceIndexFactory.SourceParentDefinitionBase
import funcify.feature.naming.ConventionalName
import funcify.feature.naming.StandardNamingConventions
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import graphql.schema.GraphQLAppliedDirective
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLInputFieldsContainer
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLNamedOutputType
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLOutputType
import graphql.schema.GraphQLTypeReference
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf

/**
 *
 * @author smccarron
 * @created 4/10/22
 */
internal class DefaultGraphQLSourceIndexFactory : GraphQLSourceIndexFactory {

    companion object {

        internal class DefaultRootContainerTypeSpec(
            private val key: DataSource.Key<GraphQLSourceIndex>
        ) : RootSourceContainerTypeSpec {

            override fun forGraphQLQueryObjectType(
                queryObjectType: GraphQLObjectType,
                metadataFilter: GraphQLApiSourceMetadataFilter
            ): GraphQLSourceContainerType {
                return createRootSourceContainerType(
                    queryObjectType,
                    queryObjectType.fieldDefinitions
                        .filter { gfd: GraphQLFieldDefinition ->
                            metadataFilter.includeGraphQLFieldDefinition(gfd)
                        }
                        .fold(persistentSetOf()) { ps, fd ->
                            ps.add(createRootAttributeForFieldDefinition(fd))
                        }
                )
            }
            private fun createRootSourceContainerType(
                queryObjectType: GraphQLObjectType,
                graphQLSourceAttributes: PersistentSet<GraphQLSourceAttribute>
            ): DefaultGraphQLSourceContainerType {
                return DefaultGraphQLSourceContainerType(
                    dataSourceLookupKey = key,
                    name = StandardNamingConventions.SNAKE_CASE.deriveName("query"),
                    sourcePath = SchematicPath.getRootPath(),
                    dataType = queryObjectType,
                    sourceAttributes = graphQLSourceAttributes
                )
            }
            private fun createRootAttributeForFieldDefinition(
                fieldDefinition: GraphQLFieldDefinition
            ): GraphQLSourceAttribute {
                val convPathName =
                    GraphQLSourceNamingConventions
                        .getPathNamingConventionForGraphQLFieldDefinitions()
                        .deriveName(fieldDefinition)
                val convFieldName =
                    GraphQLSourceNamingConventions
                        .getFieldNamingConventionForGraphQLFieldDefinitions()
                        .deriveName(fieldDefinition)
                val sourcePath: SchematicPath =
                    SchematicPath.getRootPath().transform {
                        pathSegment(convPathName.qualifiedForm)
                    }

                return DefaultGraphQLSourceAttribute(
                    dataSourceLookupKey = key,
                    sourcePath = sourcePath,
                    name = convFieldName,
                    schemaFieldDefinition = fieldDefinition
                )
            }
        }

        internal class DefaultAttributeBase(private val key: DataSource.Key<GraphQLSourceIndex>) :
            AttributeBase {

            override fun forAttributePathAndDefinition(
                attributePath: SchematicPath,
                attributeDefinition: GraphQLFieldDefinition
            ): Option<GraphQLSourceContainerType> {
                when (GraphQLFieldsContainerTypeExtractor.invoke(attributeDefinition.type)) {
                    is Some -> {
                        return DefaultGraphQLSourceContainerType(
                                dataSourceLookupKey = key,
                                sourcePath = attributePath,
                                name =
                                    GraphQLSourceNamingConventions
                                        .getFieldNamingConventionForGraphQLFieldDefinitions()
                                        .deriveName(attributeDefinition),
                                dataType = attributeDefinition.type
                            )
                            .some()
                    }
                    else -> {
                        return none<DefaultGraphQLSourceContainerType>()
                    }
                }
            }
        }

        internal class DefaultParentDefinitionBase(
            private val key: DataSource.Key<GraphQLSourceIndex>
        ) : SourceParentDefinitionBase {

            override fun withParentPathAndDefinition(
                parentPath: SchematicPath,
                parentDefinition: GraphQLFieldDefinition
            ): ChildAttributeSpec {
                return DefaultChildAttributeSpec(
                    dataSourceLookupKey = key,
                    parentPath = parentPath,
                    parentDefinition = parentDefinition
                )
            }
        }

        internal class DefaultChildAttributeSpec(
            private val dataSourceLookupKey: DataSource.Key<GraphQLSourceIndex>,
            private val parentPath: SchematicPath,
            private val parentDefinition: GraphQLFieldDefinition,
        ) : ChildAttributeSpec {

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
                if (GraphQLFieldsContainerTypeExtractor.invoke(parentDefinition.type)
                        .filter { gqlObjType ->
                            !gqlObjType.fieldDefinitions.contains(childDefinition)
                        }
                        .isDefined()
                ) {
                    val message =
                        """child_definition [ name: ${childDefinition.name} ] 
                           |is not found in child_field_definitions 
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
                    GraphQLSourceNamingConventions
                        .getPathNamingConventionForGraphQLFieldDefinitions()
                        .deriveName(childDefinition)
                val childConvFieldName =
                    GraphQLSourceNamingConventions
                        .getFieldNamingConventionForGraphQLFieldDefinitions()
                        .deriveName(childDefinition)
                val childPath =
                    parentPath.transform { pathSegment(childConvPathName.qualifiedForm) }
                return DefaultGraphQLSourceAttribute(
                    dataSourceLookupKey = dataSourceLookupKey,
                    sourcePath = childPath,
                    name = childConvFieldName,
                    schemaFieldDefinition = childDefinition
                )
            }
        }

        internal class DefaultSourceContainerTypeUpdateSpec(
            private val graphQLSourceContainerType: GraphQLSourceContainerType
        ) : SourceContainerTypeUpdateSpec {
            override fun withChildSourceAttributes(
                graphQLSourceAttributes: ImmutableSet<GraphQLSourceAttribute>
            ): GraphQLSourceContainerType {
                val validatedAttributeSet =
                    graphQLSourceAttributes
                        .stream()
                        .reduce(
                            persistentSetOf<GraphQLSourceAttribute>(),
                            { ps, gsa ->
                                if (gsa.sourcePath
                                        .getParentPath()
                                        .filter { sp ->
                                            sp != graphQLSourceContainerType.sourcePath
                                        }
                                        .isDefined()
                                ) {
                                    throw GQLDataSourceException(
                                        GQLDataSourceErrorResponse.INVALID_INPUT,
                                        """source path for attribute [ source_path: ${gsa.sourcePath} ] 
                                    |is not child path of source_container_type 
                                    |source_path [ source_path: ${graphQLSourceContainerType.sourcePath} ]
                                    |""".flattenIntoOneLine()
                                    )
                                } else {
                                    ps.add(gsa)
                                }
                            },
                            { ps1, ps2 -> ps1.addAll(ps2) }
                        )
                return (graphQLSourceContainerType as? DefaultGraphQLSourceContainerType)?.copy(
                    sourceAttributes = validatedAttributeSet
                )
                    ?: DefaultGraphQLSourceContainerType(
                        dataSourceLookupKey = graphQLSourceContainerType.dataSourceLookupKey,
                        sourcePath = graphQLSourceContainerType.sourcePath,
                        dataType = graphQLSourceContainerType.dataType as? GraphQLOutputType
                                ?: throw GQLDataSourceException(
                                    GQLDataSourceErrorResponse.SCHEMA_CREATION_ERROR,
                                    """source_container_type must have graphql_output_type, 
                                        |not an input or other kind of type""".flattenIntoOneLine()
                                ),
                        name = graphQLSourceContainerType.name,
                        sourceAttributes = validatedAttributeSet
                    )
            }
        }

        internal class DefaultParameterContainerTypeUpdateSpec : ParameterContainerTypeUpdateSpec {

            override fun withChildSourceAttributes(
                graphQLParameterAttributes: ImmutableSet<GraphQLParameterAttribute>
            ): GraphQLParameterContainerType {
                TODO("Not yet implemented")
            }
        }
        internal class DefaultParameterParentDefinitionBase(
            private val key: DataSource.Key<GraphQLSourceIndex>
        ) : ParameterParentDefinitionBase {

            override fun withParentPathAndDefinition(
                parentPath: SchematicPath,
                parentDefinition: GraphQLFieldDefinition
            ): ParameterAttributeSpec {
                if (parentPath.pathSegments.isEmpty() ||
                        parentPath.arguments.isNotEmpty() ||
                        parentPath.directives.isNotEmpty() ||
                        !parentPath
                            .pathSegments
                            .lastOrNone()
                            .filter { lastSegment ->
                                parentDefinition.name.contains(
                                    StandardNamingConventions.SNAKE_CASE
                                        .deriveName(lastSegment)
                                        .toString(),
                                    ignoreCase = true
                                )
                            }
                            .isDefined()
                ) {
                    throw GQLDataSourceException(
                        GQLDataSourceErrorResponse.INVALID_INPUT,
                        """parent_path does not reflect 
                           |graphql_field_definition: [
                           |parent_path: ${parentPath}, 
                           |graphql_field_definition: ${parentDefinition.name}
                           |]""".flattenIntoOneLine()
                    )
                }
                return DefaultParameterAttributeSpec(key, parentPath, parentDefinition)
            }
        }
        internal class DefaultParameterAttributeSpec(
            private val key: DataSource.Key<GraphQLSourceIndex>,
            private val parentPath: SchematicPath,
            private val parentDefinition: GraphQLFieldDefinition
        ) : ParameterAttributeSpec {

            override fun forChildArgument(
                childArgument: GraphQLArgument
            ): GraphQLParameterAttribute {
                if (parentDefinition.getArgument(childArgument.name) == null) {
                    val parentFieldDefinitionArgumentNames =
                        parentDefinition
                            .arguments
                            .asSequence()
                            .map { gqlArg -> gqlArg.name }
                            .joinToString(", ", "{ ", " }")
                    throw GQLDataSourceException(
                        GQLDataSourceErrorResponse.INVALID_INPUT,
                        """child_argument_definition must be present 
                            |given parent field_definition: 
                            |[ child_argument_definition.name: 
                            |${childArgument.name}, 
                            |parent_field_definition.arguments.names: 
                            |${parentFieldDefinitionArgumentNames} 
                            |]""".flattenIntoOneLine()
                    )
                }
                val argumentConventionalName: ConventionalName =
                    StandardNamingConventions.CAMEL_CASE.deriveName(childArgument.name)
                val argumentPath: SchematicPath =
                    parentPath.transform { argument(argumentConventionalName.toString()) }
                return DefaultGraphQLParameterArgumentAttribute(
                    sourcePath = argumentPath,
                    name = argumentConventionalName,
                    dataType = childArgument.type,
                    dataSourceLookupKey = key,
                    argument = childArgument.some()
                )
            }

            override fun forChildDirective(
                childDirective: GraphQLAppliedDirective
            ): GraphQLParameterAttribute {
                if (!parentDefinition.hasAppliedDirective(childDirective.name)) {
                    val appliedDirectiveNames: String =
                        parentDefinition
                            .appliedDirectives
                            .asSequence()
                            .map { gqld -> gqld.name }
                            .joinToString(", ", "{ ", " }")
                    throw GQLDataSourceException(
                        GQLDataSourceErrorResponse.INVALID_INPUT,
                        """child_directive must be present on 
                           |parent_field_definition: 
                           |[ expected: one of ${appliedDirectiveNames}, 
                           |actual: ${childDirective.name} ]
                           |""".flattenIntoOneLine()
                    )
                }
                val directiveConventionalName: ConventionalName =
                    StandardNamingConventions.CAMEL_CASE.deriveName(childDirective.name)
                val directivePath: SchematicPath =
                    parentPath.transform { directive(directiveConventionalName.toString()) }
                return DefaultGraphQLParameterDirectiveAttribute(
                    sourcePath = directivePath,
                    name = directiveConventionalName,
                    dataType = GraphQLTypeReference(childDirective.name),
                    dataSourceLookupKey = key,
                    directive = childDirective.some()
                )
            }
        }
        internal class DefaultParameterContainerTypeBase(
            private val key: DataSource.Key<GraphQLSourceIndex>
        ) : ParameterContainerTypeBase {

            override fun forArgumentPathAndDefinition(
                argumentPath: SchematicPath,
                argumentDefinition: GraphQLArgument,
            ): Option<GraphQLParameterContainerType> {
                val argumentInputObjectType: GraphQLInputObjectType =
                    when (val argumentInputFieldsContainerType: GraphQLInputFieldsContainer? =
                            GraphQLInputFieldsContainerTypeExtractor.invoke(argumentDefinition.type)
                                .orNull()
                    ) {
                        null -> {
                            throw GQLDataSourceException(
                                GQLDataSourceErrorResponse.INVALID_INPUT,
                                """child_argument_definition.type is not an 
                                   |input_fields_container type and 
                                   |therefore cannot serve as a 
                                   |parameter_container_type: [ expected: 
                                   |subtype of ${GraphQLInputFieldsContainer::class.simpleName}, 
                                   |actual: ${argumentDefinition.type::class.simpleName} ]
                                   |""".flattenIntoOneLine()
                            )
                        }
                        is GraphQLInputObjectType -> argumentInputFieldsContainerType
                        else -> {
                            throw GQLDataSourceException(
                                GQLDataSourceErrorResponse.UNEXPECTED_ERROR,
                                """new subtype of graphql_input_fields_container 
                                   |added to API that is not yet handled: [ 
                                   |subtype: 
                                   |${argumentInputFieldsContainerType::class.qualifiedName} 
                                   |]""".flattenIntoOneLine()
                            )
                        }
                    }
                TODO("Not yet implemented")
            }

            override fun forInputParameterContainerPathAndDefinition(
                inputParameterContainerPath: SchematicPath,
                inputObjectType: GraphQLInputObjectType,
            ): Option<GraphQLParameterContainerType> {
                TODO("Not yet implemented")
            }

            override fun forDirectivePathAndDefinition(
                directivePath: SchematicPath,
                directive: GraphQLAppliedDirective,
            ): Option<GraphQLParameterContainerType> {
                TODO("Not yet implemented")
            }
        }
    }

    override fun createRootSourceContainerTypeForDataSourceKey(
        key: DataSource.Key<GraphQLSourceIndex>
    ): RootSourceContainerTypeSpec {
        return DefaultRootContainerTypeSpec(key)
    }

    override fun createSourceContainerTypeForDataSourceKey(
        key: DataSource.Key<GraphQLSourceIndex>
    ): AttributeBase {
        return DefaultAttributeBase(key)
    }

    override fun updateSourceContainerType(
        graphQLSourceContainerType: GraphQLSourceContainerType
    ): SourceContainerTypeUpdateSpec {
        return DefaultSourceContainerTypeUpdateSpec(graphQLSourceContainerType)
    }

    override fun createSourceAttributeForDataSourceKey(
        key: DataSource.Key<GraphQLSourceIndex>
    ): SourceParentDefinitionBase {
        return DefaultParentDefinitionBase(key)
    }

    override fun createParameterContainerTypeForDataSourceKey(
        key: DataSource.Key<GraphQLSourceIndex>
    ): ParameterContainerTypeBase {
        return DefaultParameterContainerTypeBase(key)
    }

    override fun updateParameterContainerType(
        graphQLParameterContainerType: GraphQLParameterContainerType
    ): ParameterContainerTypeUpdateSpec {
        TODO("Not yet implemented")
    }

    override fun createParameterAttributeForDataSourceKey(
        key: DataSource.Key<GraphQLSourceIndex>
    ): ParameterParentDefinitionBase {
        return DefaultParameterParentDefinitionBase(key)
    }
}

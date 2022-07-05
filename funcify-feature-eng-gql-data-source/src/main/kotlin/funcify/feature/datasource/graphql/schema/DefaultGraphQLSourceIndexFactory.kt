package funcify.feature.datasource.graphql.schema

import arrow.core.Some
import arrow.core.filterIsInstance
import arrow.core.getOrElse
import arrow.core.identity
import arrow.core.lastOrNone
import arrow.core.some
import arrow.core.toOption
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import funcify.feature.datasource.graphql.error.GQLDataSourceErrorResponse
import funcify.feature.datasource.graphql.error.GQLDataSourceException
import funcify.feature.datasource.graphql.metadata.GraphQLApiSourceMetadataFilter
import funcify.feature.datasource.graphql.naming.GraphQLSourceNamingConventions
import funcify.feature.datasource.graphql.schema.GraphQLSourceIndexFactory.*
import funcify.feature.naming.ConventionalName
import funcify.feature.naming.StandardNamingConventions
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import funcify.feature.tools.extensions.TryExtensions.failure
import funcify.feature.tools.extensions.TryExtensions.success
import graphql.schema.*
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
            ): Try<GraphQLSourceContainerType> {
                return queryObjectType
                    .fieldDefinitions
                    .filter { gfd: GraphQLFieldDefinition ->
                        metadataFilter.includeGraphQLFieldDefinition(gfd)
                    }
                    .fold(Try.success(persistentSetOf<GraphQLSourceAttribute>())) { psAttempt, fd ->
                        psAttempt.flatMap { ps ->
                            createRootAttributeForFieldDefinition(fd).map { attr -> ps.add(attr) }
                        }
                    }
                    .map { ps -> createRootSourceContainerType(queryObjectType, ps) }
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
            ): Try<GraphQLSourceAttribute> {
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
                    .success()
            }
        }

        internal class DefaultAttributeBase(private val key: DataSource.Key<GraphQLSourceIndex>) :
            AttributeBase {

            override fun forAttributePathAndDefinition(
                attributePath: SchematicPath,
                attributeDefinition: GraphQLFieldDefinition
            ): Try<GraphQLSourceContainerType> {
                return when (GraphQLOutputFieldsContainerTypeExtractor.invoke(
                        attributeDefinition.type
                    )
                ) {
                    is Some -> {
                        DefaultGraphQLSourceContainerType(
                                dataSourceLookupKey = key,
                                sourcePath = attributePath,
                                name =
                                    GraphQLSourceNamingConventions
                                        .getFieldNamingConventionForGraphQLFieldDefinitions()
                                        .deriveName(attributeDefinition),
                                dataType = attributeDefinition.type
                            )
                            .success()
                    }
                    else -> {
                        GQLDataSourceException(
                                GQLDataSourceErrorResponse.INVALID_INPUT,
                                """field_definition.type is not a graphql_fields_container_type 
                                   |and cannot represent a graphql_source_container_type: 
                                   |[ actual: ${attributeDefinition.type::class.qualifiedName} 
                                   |]""".flattenIntoOneLine()
                            )
                            .failure()
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
            ): Try<GraphQLSourceAttribute> {
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
                    val parentDefinitionPathNameSegment =
                        GraphQLSourceNamingConventions
                            .getPathNamingConventionForGraphQLFieldDefinitions()
                            .deriveName(parentDefinition)
                            .qualifiedForm
                    val message =
                        """parent_path [ path: ${parentPath.toURI()} ] does not match expected requirements 
                           |for parent_definition input [ qualified_name: $parentDefinitionPathNameSegment
                           |""".flattenIntoOneLine()
                    return GQLDataSourceException(GQLDataSourceErrorResponse.INVALID_INPUT, message)
                        .failure<GraphQLSourceAttribute>()
                }
                if (GraphQLOutputFieldsContainerTypeExtractor.invoke(parentDefinition.type)
                        .filter { gqlObjType ->
                            !gqlObjType.fieldDefinitions.contains(childDefinition)
                        }
                        .isDefined()
                ) {
                    val parentDefinitionTypeName =
                        parentDefinition
                            .type
                            .toOption()
                            .filterIsInstance<GraphQLNamedOutputType>()
                            .map(GraphQLNamedOutputType::getName)
                            .getOrElse { "<NA>" }
                    val message =
                        """child_definition [ name: ${childDefinition.name} ] 
                           |is not found in child_field_definitions 
                           |of parent_definition object_type or wrapped object_type 
                           |[ name: $parentDefinitionTypeName ]
                           |""".flattenIntoOneLine()
                    return GQLDataSourceException(GQLDataSourceErrorResponse.INVALID_INPUT, message)
                        .failure()
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
                    .success()
            }
        }

        internal class DefaultSourceContainerTypeUpdateSpec(
            private val graphQLSourceContainerType: GraphQLSourceContainerType
        ) : SourceContainerTypeUpdateSpec {
            override fun withChildSourceAttributes(
                graphQLSourceAttributes: ImmutableSet<GraphQLSourceAttribute>
            ): Try<GraphQLSourceContainerType> {
                val validatedAttributeSet =
                    graphQLSourceAttributes
                        .stream()
                        .reduce(
                            Try.success(persistentSetOf<GraphQLSourceAttribute>()),
                            { psAttempt, gsa ->
                                if (gsa.sourcePath
                                        .getParentPath()
                                        .filter { sp ->
                                            sp != graphQLSourceContainerType.sourcePath
                                        }
                                        .isDefined()
                                ) {
                                    GQLDataSourceException(
                                            GQLDataSourceErrorResponse.INVALID_INPUT,
                                            """source path for attribute [ source_path: ${gsa.sourcePath} ] 
                                               |is not child path of source_container_type 
                                               |source_path [ source_path: ${graphQLSourceContainerType.sourcePath} ]
                                               |""".flattenIntoOneLine()
                                        )
                                        .failure<PersistentSet<GraphQLSourceAttribute>>()
                                } else {
                                    psAttempt.map { ps -> ps.add(gsa) }
                                }
                            },
                            { ps1Attempt, ps2Attempt ->
                                ps1Attempt.flatMap { ps1 ->
                                    ps2Attempt.map { ps2 -> ps1.addAll(ps2) }
                                }
                            }
                        )
                if (validatedAttributeSet.isFailure()) {
                    return validatedAttributeSet.getFailure().orNull()!!.failure()
                }
                return ((graphQLSourceContainerType as? DefaultGraphQLSourceContainerType)?.copy(
                        sourceAttributes = validatedAttributeSet.orNull()!!
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
                            sourceAttributes = validatedAttributeSet.orNull()!!
                        ))
                    .success()
            }
        }

        internal class DefaultParameterContainerTypeUpdateSpec(
            private val graphQLParameterContainerType: GraphQLParameterContainerType
        ) : ParameterContainerTypeUpdateSpec {

            override fun withChildSourceAttributes(
                graphQLParameterAttributes: ImmutableSet<GraphQLParameterAttribute>
            ): Try<GraphQLParameterContainerType> {
                when (graphQLParameterContainerType) {
                    is DefaultGraphQLParameterDirectiveContainerType -> {
                        val directiveArgumentTypesByName =
                            graphQLParameterContainerType
                                .graphQLAppliedDirective
                                .map { directive: GraphQLAppliedDirective -> directive.arguments }
                                .fold(::emptyList, ::identity)
                                .stream()
                                .map { dir -> dir.name to dir.type }
                                .reducePairsToPersistentMap()
                        return graphQLParameterAttributes
                            .asSequence()
                            .fold(Try.success(persistentSetOf<GraphQLParameterAttribute>())) {
                                psAttempt,
                                gqlParmAttr ->
                                psAttempt.flatMap { ps ->
                                    Try.attempt {
                                        if (gqlParmAttr.name.toString() in
                                                directiveArgumentTypesByName &&
                                                gqlParmAttr
                                                    .appliedDirectiveArgument
                                                    .filter { appDirArg ->
                                                        appDirArg.type ==
                                                            directiveArgumentTypesByName[
                                                                gqlParmAttr.name.toString()]
                                                    }
                                                    .isDefined()
                                        ) {
                                            gqlParmAttr
                                        } else {
                                            throw GQLDataSourceException(
                                                GQLDataSourceErrorResponse.INVALID_INPUT,
                                                """graphql_parameter_attribute not matching 
                                                    |directive_argument.name and directive_argument.type 
                                                    |of parent: [ actual: { name: ${gqlParmAttr.name}, 
                                                    |type: ${gqlParmAttr.dataType} }""".flattenIntoOneLine()
                                            )
                                        }
                                    }
                                        .map { gpa -> ps.add(gpa) }
                                }
                            }
                            .map { gqlParmAttrs ->
                                graphQLParameterContainerType.copy(
                                    parameterAttributes = gqlParmAttrs
                                )
                            }
                    }
                    is DefaultGraphQLParameterInputObjectContainerType -> {
                        val inputObjFieldTypesByName =
                            graphQLParameterContainerType
                                .graphQLInputFieldsContainerType
                                .map { ifc -> ifc.fieldDefinitions }
                                .fold(::emptyList, ::identity)
                                .stream()
                                .map { gqlif -> gqlif.name to gqlif.type }
                                .reducePairsToPersistentMap()
                        return graphQLParameterAttributes
                            .asSequence()
                            .fold(Try.success(persistentSetOf<GraphQLParameterAttribute>())) {
                                psAttempt,
                                gqlParamAttr ->
                                psAttempt.flatMap { ps ->
                                    Try.attempt {
                                        if (gqlParamAttr.name.toString() in
                                                inputObjFieldTypesByName &&
                                                gqlParamAttr
                                                    .inputObjectField
                                                    .filter { gqlif ->
                                                        gqlif.type ==
                                                            inputObjFieldTypesByName[
                                                                gqlParamAttr.name.toString()]
                                                    }
                                                    .isDefined()
                                        ) {
                                            gqlParamAttr
                                        } else {
                                            throw GQLDataSourceException(
                                                GQLDataSourceErrorResponse.INVALID_INPUT,
                                                """graphql_parameter_attribute name and/or type does 
                                            |not match those of graphql_parameter_container_type 
                                            |input_object_field values: [ actual: 
                                            |{ name: ${gqlParamAttr.name}, 
                                            |type: ${gqlParamAttr.dataType} } ]
                                            |""".flattenIntoOneLine()
                                            )
                                        }
                                    }
                                        .map { gqlpa -> ps.add(gqlpa) }
                                }
                            }
                            .map { gqlpas ->
                                graphQLParameterContainerType.copy(parameterAttributes = gqlpas)
                            }
                    }
                    else -> {
                        return GQLDataSourceException(
                                GQLDataSourceErrorResponse.UNEXPECTED_ERROR,
                                """a graphql_parameter_source_container_type 
                                |is not handled: [ container_type: 
                                |${graphQLParameterContainerType::class.qualifiedName} ]
                                |""".flattenIntoOneLine()
                            )
                            .failure()
                    }
                }
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
            ): Try<GraphQLParameterAttribute> {
                if (parentDefinition.getArgument(childArgument.name) == null) {
                    val parentFieldDefinitionArgumentNames =
                        parentDefinition
                            .arguments
                            .asSequence()
                            .map { gqlArg -> gqlArg.name }
                            .joinToString(", ", "{ ", " }")
                    return GQLDataSourceException(
                            GQLDataSourceErrorResponse.INVALID_INPUT,
                            """child_argument_definition must be present 
                               |given parent field_definition: 
                               |[ child_argument_definition.name: 
                               |${childArgument.name}, 
                               |parent_field_definition.arguments.names: 
                               |${parentFieldDefinitionArgumentNames} 
                               |]""".flattenIntoOneLine()
                        )
                        .failure<GraphQLParameterAttribute>()
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
                    .success()
            }

            override fun forChildDirective(
                childDirective: GraphQLAppliedDirective
            ): Try<GraphQLParameterAttribute> {
                if (!parentDefinition.hasAppliedDirective(childDirective.name)) {
                    val appliedDirectiveNames: String =
                        parentDefinition
                            .appliedDirectives
                            .asSequence()
                            .map { gqld -> gqld.name }
                            .joinToString(", ", "{ ", " }")
                    return GQLDataSourceException(
                            GQLDataSourceErrorResponse.INVALID_INPUT,
                            """child_directive must be present on 
                               |parent_field_definition: 
                               |[ expected: one of ${appliedDirectiveNames}, 
                               |actual: ${childDirective.name} ]
                               |""".flattenIntoOneLine()
                        )
                        .failure()
                }
                val directiveConventionalName: ConventionalName =
                    StandardNamingConventions.CAMEL_CASE.deriveName(childDirective.name)
                val directivePath: SchematicPath =
                    parentPath.transform { directive(directiveConventionalName.toString()) }
                val attributeTypeName: ConventionalName =
                    StandardNamingConventions.PASCAL_CASE.deriveName(
                        childDirective.name + " Directive"
                    )
                val attributeType: GraphQLInputType =
                    GraphQLTypeReference(attributeTypeName.toString())
                return DefaultGraphQLParameterDirectiveAttribute(
                        sourcePath = directivePath,
                        name = directiveConventionalName,
                        dataType = attributeType,
                        dataSourceLookupKey = key,
                        directive = childDirective.some()
                    )
                    .success()
            }
        }
        internal class DefaultParameterContainerTypeBase(
            private val key: DataSource.Key<GraphQLSourceIndex>
        ) : ParameterContainerTypeBase {

            override fun forArgumentNameAndInputObjectType(
                name: String,
                inputObjectType: GraphQLInputObjectType,
            ): InputObjectTypeContainerSpec {
                return DefaultInputObjectTypeContainerSpec(
                    key = key,
                    name = name,
                    inputObjectType = inputObjectType
                )
            }

            override fun forDirective(
                directive: GraphQLAppliedDirective,
            ): DirectiveContainerTypeSpec {
                return DefaultDirectiveContainerTypeSpec(key = key, appliedDirective = directive)
            }
        }

        internal class DefaultInputObjectTypeContainerSpec(
            private val key: DataSource.Key<GraphQLSourceIndex>,
            private val name: String,
            private val inputObjectType: GraphQLInputObjectType
        ) : InputObjectTypeContainerSpec {

            override fun onFieldArgumentValue(
                argumentPath: SchematicPath,
                graphQLArgument: GraphQLArgument
            ): Try<GraphQLParameterContainerType> {
                if (argumentPath.arguments.isEmpty()) {
                    return GQLDataSourceException(
                            GQLDataSourceErrorResponse.INVALID_INPUT,
                            """source_path for graphql_argument must 
                               |have at least one argument: 
                               |[ actual: ${argumentPath} ]
                               |""".flattenIntoOneLine()
                        )
                        .failure()
                }
                if (!argumentPath
                        .arguments
                        .asSequence()
                        .firstOrNull()
                        .toOption()
                        .filter { (argName, _) -> argName == graphQLArgument.name }
                        .isDefined()
                ) {
                    val firstArgumentName: String =
                        argumentPath
                            .arguments
                            .asSequence()
                            .firstOrNull()
                            .toOption()
                            .map { entry -> entry.key }
                            .orNull()
                            ?: "<NA>"
                    return GQLDataSourceException(
                            GQLDataSourceErrorResponse.INVALID_INPUT,
                            """source_path.arguments[0].key does not represent 
                               |graphql_argument: [ expected: ${graphQLArgument.name}, 
                               |actual: $firstArgumentName ]
                               |""".flattenIntoOneLine()
                        )
                        .failure()
                }
                val parameterContainerPath =
                    argumentPath.transform {
                        clearArguments()
                            .clearDirectives()
                            .argument(
                                graphQLArgument.name,
                                JsonNodeFactory.instance.objectNode().putNull(name)
                            )
                    }
                return when (val graphQLInputFieldsContainer: GraphQLInputFieldsContainer? =
                        GraphQLInputFieldsContainerTypeExtractor.invoke(graphQLArgument.type)
                            .orNull()
                ) {
                    null -> {
                        GQLDataSourceException(
                                GQLDataSourceErrorResponse.INVALID_INPUT,
                                """graphql_argument_value_type is not a 
                                       |graphql_input_fields_container subtype and 
                                       |therefore cannot be made a graphql_parameter_container_type: 
                                       |[ actual_datatype: ${graphQLArgument.type::class.qualifiedName} 
                                       |]""".flattenIntoOneLine()
                            )
                            .failure()
                    }
                    else -> {
                        DefaultGraphQLParameterInputObjectContainerType(
                                sourcePath = parameterContainerPath,
                                name =
                                    StandardNamingConventions.PASCAL_CASE.deriveName(
                                        inputObjectType.name
                                    ),
                                dataType = inputObjectType,
                                dataSourceLookupKey = key,
                            )
                            .success()
                    }
                }
            }

            override fun onDirectiveArgumentValue(
                directivePath: SchematicPath,
                graphQLAppliedDirective: GraphQLAppliedDirective,
            ): Try<GraphQLParameterContainerType> {
                if (directivePath.directives.isEmpty()) {
                    return GQLDataSourceException(
                            GQLDataSourceErrorResponse.INVALID_INPUT,
                            """directive_path does not represent a directive 
                               |on a source_index: [ actual: ${directivePath} ]
                               |""".flattenIntoOneLine()
                        )
                        .failure()
                }
                if (directivePath.directives.none { (name, jsonVal) ->
                        name == graphQLAppliedDirective.name && (jsonVal.isEmpty || jsonVal.isNull)
                    }
                ) {
                    return GQLDataSourceException(
                            GQLDataSourceErrorResponse.INVALID_INPUT,
                            """directive_path.directives does not contain 
                               |the given applied_directive.name (or if present, 
                               |does not represent empty or null container directive): 
                               |[ actual: ${directivePath} ]""".flattenIntoOneLine()
                        )
                        .failure()
                }
                return DefaultGraphQLParameterDirectiveContainerType(
                        sourcePath = directivePath,
                        graphQLAppliedDirective = graphQLAppliedDirective.some(),
                        dataSourceLookupKey = key
                    )
                    .success()
            }
        }

        internal class DefaultDirectiveContainerTypeSpec(
            private val key: DataSource.Key<GraphQLSourceIndex>,
            private val appliedDirective: GraphQLAppliedDirective
        ) : DirectiveContainerTypeSpec {

            override fun onFieldDefinition(
                sourceAttributePath: SchematicPath,
                graphQLFieldDefinition: GraphQLFieldDefinition,
            ): Try<GraphQLParameterContainerType> {
                if (sourceAttributePath.arguments.isNotEmpty() ||
                        sourceAttributePath.directives.isNotEmpty()
                ) {
                    return GQLDataSourceException(
                            GQLDataSourceErrorResponse.INVALID_INPUT,
                            """source_attribute_path cannot have any arguments 
                               |or directives; must represent source_attribute: 
                               |[ actual: ${sourceAttributePath} ]
                               |""".flattenIntoOneLine()
                        )
                        .failure()
                }
                if (!graphQLFieldDefinition.hasAppliedDirective(appliedDirective.name)) {
                    return GQLDataSourceException(
                            GQLDataSourceErrorResponse.INVALID_INPUT,
                            """graphql_field_definition does not have 
                               |applied_directive with name within 
                               |applied_directives set: [ applied_directive.name: 
                               |${appliedDirective.name} ]
                               |""".flattenIntoOneLine()
                        )
                        .failure()
                }
                val directivePath: SchematicPath =
                    sourceAttributePath.transform { directive(appliedDirective.name) }
                return DefaultGraphQLParameterDirectiveContainerType(
                        sourcePath = directivePath,
                        dataSourceLookupKey = key,
                        graphQLAppliedDirective = appliedDirective.some()
                    )
                    .success()
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
        return DefaultParameterContainerTypeUpdateSpec(graphQLParameterContainerType)
    }

    override fun createParameterAttributeForDataSourceKey(
        key: DataSource.Key<GraphQLSourceIndex>
    ): ParameterParentDefinitionBase {
        return DefaultParameterParentDefinitionBase(key)
    }
}

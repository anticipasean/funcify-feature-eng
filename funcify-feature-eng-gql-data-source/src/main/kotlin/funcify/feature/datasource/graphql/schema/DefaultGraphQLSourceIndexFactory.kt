package funcify.feature.datasource.graphql.schema

import arrow.core.Option
import arrow.core.Some
import arrow.core.filterIsInstance
import arrow.core.getOrElse
import arrow.core.identity
import arrow.core.lastOrNone
import arrow.core.none
import arrow.core.some
import arrow.core.toOption
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.contains
import funcify.feature.datasource.graphql.error.GQLDataSourceErrorResponse
import funcify.feature.datasource.graphql.error.GQLDataSourceException
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
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import funcify.feature.tools.extensions.TryExtensions.successIfNonNull
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
                queryObjectType: GraphQLObjectType
            ): Try<GraphQLSourceContainerType> {
                return createRootSourceContainerType(queryObjectType).successIfNonNull()
            }

            private fun createRootSourceContainerType(
                queryObjectType: GraphQLObjectType
            ): DefaultGraphQLSourceContainerType {
                return DefaultGraphQLSourceContainerType(
                    dataSourceLookupKey = key,
                    name = StandardNamingConventions.SNAKE_CASE.deriveName("query"),
                    sourcePath = SchematicPath.getRootPath(),
                    dataType = queryObjectType,
                    sourceAttributes = persistentSetOf()
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
                        graphQLFieldDefinition = fieldDefinition
                    )
                    .successIfNonNull()
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
                            .successIfNonNull()
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
                    parentPathIfNotRoot = parentPath.some(),
                    parentDefinitionIfNotRoot = parentDefinition.some(),
                    queryRootObjectTypeIfRoot = none()
                )
            }

            override fun withParentRootContainerType(
                queryRootObjectType: GraphQLObjectType
            ): ChildAttributeSpec {
                return DefaultChildAttributeSpec(
                    dataSourceLookupKey = key,
                    parentPathIfNotRoot = none(),
                    parentDefinitionIfNotRoot = none(),
                    queryRootObjectTypeIfRoot = queryRootObjectType.some()
                )
            }
        }

        internal class DefaultChildAttributeSpec(
            private val dataSourceLookupKey: DataSource.Key<GraphQLSourceIndex>,
            private val parentPathIfNotRoot: Option<SchematicPath>,
            private val parentDefinitionIfNotRoot: Option<GraphQLFieldDefinition>,
            private val queryRootObjectTypeIfRoot: Option<GraphQLObjectType>,
        ) : ChildAttributeSpec {

            override fun forChildAttributeDefinition(
                childDefinition: GraphQLFieldDefinition
            ): Try<GraphQLSourceAttribute> {
                if (!queryRootObjectTypeIfRoot.isDefined()) {
                    if (!parentPathIfNotRoot.isDefined() || !parentDefinitionIfNotRoot.isDefined()
                    ) {
                        if (!parentPathIfNotRoot.isDefined()) {
                            return GQLDataSourceException(
                                    GQLDataSourceErrorResponse.INVALID_INPUT,
                                    """parent_path must be provided if parent 
                               |is field_definition and not 
                               |root_query_object_type""".flattenIntoOneLine()
                                )
                                .failure()
                        } else {
                            return GQLDataSourceException(
                                    GQLDataSourceErrorResponse.INVALID_INPUT,
                                    """parent_field_definition must be provided if parent 
                                   |is not root_query_object_type""".flattenIntoOneLine()
                                )
                                .failure()
                        }
                    }
                    val parentPath: SchematicPath = parentPathIfNotRoot.orNull()!!
                    val parentDefinition: GraphQLFieldDefinition =
                        parentDefinitionIfNotRoot.orNull()!!

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
                        return GQLDataSourceException(
                                GQLDataSourceErrorResponse.INVALID_INPUT,
                                message
                            )
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
                        return GQLDataSourceException(
                                GQLDataSourceErrorResponse.INVALID_INPUT,
                                message
                            )
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
                            graphQLFieldDefinition = childDefinition
                        )
                        .successIfNonNull()
                } else {
                    if (!queryRootObjectTypeIfRoot.isDefined()) {
                        return GQLDataSourceException(
                                GQLDataSourceErrorResponse.INVALID_INPUT,
                                """query_root_object_type must be provided if parent 
                                   |is not field_definition""".flattenIntoOneLine()
                            )
                            .failure()
                    }
                    val queryRootObjectType: GraphQLObjectType =
                        queryRootObjectTypeIfRoot.orNull()!!
                    if (childDefinition !in queryRootObjectType.fieldDefinitions) {
                        val queryRootObjectTypeFieldDefinitionNames: String =
                            queryRootObjectType.fieldDefinitions.joinToString(
                                separator = ", ",
                                prefix = "{ ",
                                postfix = " }",
                                transform = { fd -> "{ name: ${fd.name}, type: ${fd.type} }" }
                            )
                        return GQLDataSourceException(
                                GQLDataSourceErrorResponse.INVALID_INPUT,
                                """child_field_definition must be found 
                                |on query_root_object_type in order 
                                |to create graphql_source_attribute 
                                |for this definition: [ expected: 
                                |${queryRootObjectTypeFieldDefinitionNames}, 
                                |actual: { name: ${childDefinition.name}, 
                                |type: ${childDefinition.type} } ]
                                |""".flattenIntoOneLine()
                            )
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
                    return DefaultGraphQLSourceAttribute(
                            dataSourceLookupKey = dataSourceLookupKey,
                            sourcePath =
                                SchematicPath.of { pathSegment(childConvPathName.toString()) },
                            name = childConvFieldName,
                            graphQLFieldDefinition = childDefinition
                        )
                        .successIfNonNull()
                }
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
                    .successIfNonNull()
            }
        }

        internal class DefaultParameterContainerTypeUpdateSpec(
            private val graphQLParameterContainerType: GraphQLParameterContainerType
        ) : ParameterContainerTypeUpdateSpec {

            override fun withChildParameterAttributes(
                graphQLParameterAttributes: ImmutableSet<GraphQLParameterAttribute>
                                                     ): Try<GraphQLParameterContainerType> {
                when (graphQLParameterContainerType) {
                    is DefaultGraphQLParameterDirectiveArgumentContainerType -> {
                        val directiveArgumentFieldTypesByName =
                            graphQLParameterContainerType
                                .inputFieldsContainerType
                                .fieldDefinitions
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
                                                directiveArgumentFieldTypesByName &&
                                                gqlParmAttr
                                                    .directiveArgument
                                                    .filter { appDirArg ->
                                                        appDirArg.type ==
                                                            directiveArgumentFieldTypesByName[
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
                                .inputFieldsContainerType
                                .fieldDefinitions
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

            override fun withParentPathAndFieldDefinition(
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

            override fun withParentPathAndAppliedDirective(
                parentPath: SchematicPath,
                parentAppliedDirective: GraphQLAppliedDirective,
            ): ParameterDirectiveArgumentAttributeSpec {
                return DefaultParameterDirectiveArgumentAttributeSpec(
                    key = key,
                    parentPath = parentPath,
                    parentAppliedDirective = parentAppliedDirective
                )
            }

            override fun withParentPathAndFieldArgument(
                parentPath: SchematicPath,
                parentArgument: GraphQLArgument,
            ): ParameterAttributeInputObjectFieldSpec {
                return DefaultParameterAttributeInputObjectFieldSpec(
                    key = key,
                    parentPath = parentPath,
                    parentArgument = parentArgument.some()
                )
            }

            override fun withParentPathAndDirectiveArgument(
                parentPath: SchematicPath,
                parentDirectiveArgument: GraphQLAppliedDirectiveArgument,
            ): ParameterAttributeInputObjectFieldSpec {
                return DefaultParameterAttributeInputObjectFieldSpec(
                    key = key,
                    parentPath = parentPath,
                    parentDirectiveArgument = parentDirectiveArgument.some()
                )
            }
        }

        internal class DefaultParameterDirectiveArgumentAttributeSpec(
            private val key: DataSource.Key<GraphQLSourceIndex>,
            private val parentPath: SchematicPath,
            private val parentAppliedDirective: GraphQLAppliedDirective
        ) : ParameterDirectiveArgumentAttributeSpec {

            override fun forChildDirectiveArgument(
                childDirectiveArgument: GraphQLAppliedDirectiveArgument
            ): Try<GraphQLParameterAttribute> {
                if (parentPath.directives.isEmpty()) {
                    return GQLDataSourceException(
                            GQLDataSourceErrorResponse.INVALID_INPUT,
                            """source_path for graphql_applied_directive must 
                               |have at least one directive: 
                               |[ actual: ${parentPath} ]
                               |""".flattenIntoOneLine()
                        )
                        .failure()
                }
                if (!parentPath.directives.containsKey(parentAppliedDirective.name)) {
                    val directiveNamesInGivenPath =
                        parentPath
                            .directives
                            .asSequence()
                            .map { (name, _) -> name }
                            .joinToString(", ", "{ ", " }")
                    return GQLDataSourceException(
                            GQLDataSourceErrorResponse.INVALID_INPUT,
                            """source_path for graphql_applied_directive must 
                               |have the directive_name specified: 
                               |[ actual: $directiveNamesInGivenPath ]
                               |""".flattenIntoOneLine()
                        )
                        .failure()
                }
                if (parentAppliedDirective.getArgument(childDirectiveArgument.name) != null) {
                    val appliedDirectiveArgumentNamesSet =
                        parentAppliedDirective
                            .arguments
                            .asSequence()
                            .map { arg -> arg.name }
                            .joinToString(", ", "{ ", " }")
                    return GQLDataSourceException(
                            GQLDataSourceErrorResponse.INVALID_INPUT,
                            """child_directive_argument.name must be present in 
                               |the given applied_directive's directive_arguments: 
                               |[ expected: one of ${appliedDirectiveArgumentNamesSet}, 
                               |actual: ${childDirectiveArgument.name} ]""".flattenIntoOneLine()
                        )
                        .failure()
                }
                val directiveArgumentConventionalName: ConventionalName =
                    StandardNamingConventions.CAMEL_CASE.deriveName(childDirectiveArgument.name)
                val childPath =
                    parentPath.transform {
                        directive(
                            parentAppliedDirective.name,
                            JsonNodeFactory.instance
                                .objectNode()
                                .putNull(childDirectiveArgument.name)
                        )
                    }
                return DefaultGraphQLParameterDirectiveArgumentAttribute(
                        sourcePath = childPath,
                        name = directiveArgumentConventionalName,
                        dataSourceLookupKey = key,
                        directiveArgument = childDirectiveArgument.some()
                    )
                    .successIfNonNull()
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
                return DefaultGraphQLParameterFieldArgumentAttribute(
                        sourcePath = argumentPath,
                        name = argumentConventionalName,
                        dataSourceLookupKey = key,
                        fieldArgument = childArgument.some()
                    )
                    .successIfNonNull()
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
                    StandardNamingConventions.IDENTITY.deriveName(childDirective.name)
                val directivePath: SchematicPath =
                    parentPath.transform { directive(directiveConventionalName.toString()) }
                return DefaultGraphQLParameterDirectiveAttribute(
                        sourcePath = directivePath,
                        dataSourceLookupKey = key,
                        directive = childDirective.some()
                    )
                    .successIfNonNull()
            }
        }
        internal class DefaultParameterContainerTypeBase(
            private val key: DataSource.Key<GraphQLSourceIndex>
        ) : ParameterContainerTypeBase {

            override fun forFieldArgument(
                fieldArgument: GraphQLArgument
            ): InputObjectTypeContainerSpec {
                return DefaultInputObjectTypeContainerSpec(key = key, fieldArgument = fieldArgument)
            }

            override fun forDirectiveArgument(
                directiveArgument: GraphQLAppliedDirectiveArgument,
            ): DirectiveContainerTypeSpec {
                return DefaultDirectiveContainerTypeSpec(
                    key = key,
                    directiveArgument = directiveArgument
                )
            }
        }

        internal class DefaultInputObjectTypeContainerSpec(
            private val key: DataSource.Key<GraphQLSourceIndex>,
            private val fieldArgument: GraphQLArgument
        ) : InputObjectTypeContainerSpec {

            override fun onFieldDefinition(
                parentFieldDefinitionPath: SchematicPath,
                parentFieldDefinition: GraphQLFieldDefinition,
            ): Try<GraphQLParameterContainerType> {
                if (parentFieldDefinitionPath.arguments.isNotEmpty() ||
                        parentFieldDefinitionPath.directives.isNotEmpty()
                ) {
                    return GQLDataSourceException(
                            GQLDataSourceErrorResponse.INVALID_INPUT,
                            """path for graphql_field_definition 
                               |cannot have any arguments or directives: 
                               |[ actual: ${parentFieldDefinitionPath} ]
                               |""".flattenIntoOneLine()
                        )
                        .failure()
                }
                if (parentFieldDefinition.getArgument(fieldArgument.name) == null) {
                    val parentFieldDefinitionArgumentNames: String =
                        parentFieldDefinition
                            .arguments
                            .asSequence()
                            .map { arg -> arg.name }
                            .joinToString(", ", "{ ", " }")
                    return GQLDataSourceException(
                            GQLDataSourceErrorResponse.INVALID_INPUT,
                            """parent_field_definition does not 
                                |contain argument with expected 
                                |argument name: [ expected: 
                                |one of ${parentFieldDefinitionArgumentNames}, 
                                |actual: ${fieldArgument.name} ]""".flattenIntoOneLine()
                        )
                        .failure()
                }
                val inputFieldsContainerTypeIfDefined: Try<GraphQLInputFieldsContainer> =
                    GraphQLInputFieldsContainerTypeExtractor.invoke(fieldArgument.type)
                        .successIfDefined()
                if (inputFieldsContainerTypeIfDefined.isFailure()) {
                    return GQLDataSourceException(
                            GQLDataSourceErrorResponse.INVALID_INPUT,
                            """graphql_field_argument does not have 
                               |graphql_input_fields_container_type: 
                               |[ actual: ${fieldArgument.type::class.simpleName} ]
                               |""".flattenIntoOneLine()
                        )
                        .failure()
                }
                val fieldArgumentInputFieldsContainerType =
                    inputFieldsContainerTypeIfDefined.orElseThrow()
                val parameterContainerPath =
                    parentFieldDefinitionPath.transform {
                        argument(fieldArgument.name, JsonNodeFactory.instance.objectNode())
                    }

                return DefaultGraphQLParameterFieldArgumentContainerType(
                        sourcePath = parameterContainerPath,
                        name =
                            StandardNamingConventions.PASCAL_CASE.deriveName(
                                fieldArgumentInputFieldsContainerType.name
                            ),
                        dataSourceLookupKey = key,
                        fieldArgument = fieldArgument.some()
                    )
                    .successIfNonNull()
            }
        }

        internal class DefaultDirectiveContainerTypeSpec(
            private val key: DataSource.Key<GraphQLSourceIndex>,
            private val directiveArgument: GraphQLAppliedDirectiveArgument
        ) : DirectiveContainerTypeSpec {

            override fun onParentDirective(
                parentDirectivePath: SchematicPath,
                parentAppliedDirective: GraphQLAppliedDirective,
            ): Try<GraphQLParameterContainerType> {
                if (parentDirectivePath.directives.isEmpty() ||
                        parentDirectivePath.directives.none { (name, _) ->
                            parentAppliedDirective.name == name
                        }
                ) {
                    return GQLDataSourceException(
                            GQLDataSourceErrorResponse.INVALID_INPUT,
                            """parent_directive_path must have a directive 
                               |with name: [ name: ${parentAppliedDirective.name} ]
                               |""".flattenIntoOneLine()
                        )
                        .failure()
                }
                if (parentAppliedDirective.getArgument(directiveArgument.name) == null) {
                    val directiveArgumentNamesSet: String =
                        parentAppliedDirective
                            .arguments
                            .asSequence()
                            .map { arg -> arg.name }
                            .joinToString(", ", "{ ", " }")
                    return GQLDataSourceException(
                            GQLDataSourceErrorResponse.INVALID_INPUT,
                            """parent_applied_directive does not have
                                |a directive_argument with name 
                                |[ expected: ${directiveArgument.name}, 
                                |actual: $directiveArgumentNamesSet ]
                            """.trimMargin()
                        )
                        .failure()
                }
                val graphQLInputFieldContainerTypeIfPresent: Option<GraphQLInputFieldsContainer> =
                    GraphQLInputFieldsContainerTypeExtractor.invoke(directiveArgument.type)
                if (!graphQLInputFieldContainerTypeIfPresent.isDefined()) {
                    return GQLDataSourceException(
                            GQLDataSourceErrorResponse.INVALID_INPUT,
                            """child_directive_argument.type 
                               |is not an input_fields_container_type: 
                               |[ actual.type: ${directiveArgument.type::class.qualifiedName} ]
                               |""".flattenIntoOneLine()
                        )
                        .failure()
                }
                val directiveArgumentInputObjectType: GraphQLInputFieldsContainer =
                    graphQLInputFieldContainerTypeIfPresent.orNull()!!
                val updatedJsonValueForDirective: Option<ObjectNode> =
                    parentDirectivePath
                        .directives
                        .asSequence()
                        .firstOrNull { (name, _) -> name == parentAppliedDirective.name }
                        .toOption()
                        .map { (_, jsonValue) ->
                            when (jsonValue) {
                                is ObjectNode ->
                                    JsonNodeFactory.instance
                                        .objectNode()
                                        .setAll<ObjectNode>(jsonValue)
                                        .putObject(directiveArgument.name)
                                else ->
                                    JsonNodeFactory.instance
                                        .objectNode()
                                        .putObject(directiveArgument.name)
                            }
                        }
                val directiveArgumentPath: SchematicPath =
                    parentDirectivePath.transform {
                        directive(
                            parentAppliedDirective.name,
                            updatedJsonValueForDirective.orNull()!!
                        )
                    }
                return DefaultGraphQLParameterDirectiveArgumentContainerType(
                        dataSourceLookupKey = key,
                        sourcePath = directiveArgumentPath,
                        name =
                            StandardNamingConventions.PASCAL_CASE.deriveName(
                                directiveArgumentInputObjectType.name
                            ),
                        directiveArgument = directiveArgument.some()
                    )
                    .successIfNonNull()
            }
        }

        internal class DefaultParameterAttributeInputObjectFieldSpec(
            private val key: DataSource.Key<GraphQLSourceIndex>,
            private val parentPath: SchematicPath,
            private val parentArgument: Option<GraphQLArgument> = none(),
            private val parentDirectiveArgument: Option<GraphQLAppliedDirectiveArgument> = none()
        ) : ParameterAttributeInputObjectFieldSpec {

            override fun forInputObjectField(
                childInputObjectField: GraphQLInputObjectField
            ): Try<GraphQLParameterAttribute> {
                val childInputObjectFieldConventionalName: ConventionalName =
                    StandardNamingConventions.CAMEL_CASE.deriveName(childInputObjectField.name)
                val childInputObjectFieldPathName: ConventionalName =
                    StandardNamingConventions.IDENTITY.deriveName(childInputObjectField.name)
                when {
                    parentArgument.isDefined() -> {
                        if (parentPath.arguments.isEmpty() ||
                                !parentPath
                                    .arguments
                                    .asIterable()
                                    .lastOrNone()
                                    .filter { (name, _) -> name == parentArgument.orNull()!!.name }
                                    .isDefined()
                        ) {
                            return GQLDataSourceException(
                                    GQLDataSourceErrorResponse.INVALID_INPUT,
                                    """parent_path does not represent the 
                                       |parent_field_argument: [ actual: ${parentPath}, 
                                       |expected: path with argument with 
                                       |[ name: ${parentArgument.orNull()!!.name} ] ]
                                       |""".flattenIntoOneLine()
                                )
                                .failure()
                        }
                        if (!parentArgument
                                .map { gqlArg: GraphQLArgument -> gqlArg.type }
                                .flatMap { gqlInputType: GraphQLInputType ->
                                    GraphQLInputFieldsContainerTypeExtractor.invoke(gqlInputType)
                                }
                                .filter { gqlInputFieldCont: GraphQLInputFieldsContainer ->
                                    gqlInputFieldCont.getFieldDefinition(
                                        childInputObjectField.name
                                    ) != null
                                }
                                .isDefined()
                        ) {
                            val parentArgumentInputFieldsSet: String =
                                parentArgument
                                    .map { gqlArg -> gqlArg.type }
                                    .flatMap { gqlInputType ->
                                        GraphQLInputFieldsContainerTypeExtractor.invoke(
                                            gqlInputType
                                        )
                                    }
                                    .map { gqlInputFieldsCont ->
                                        gqlInputFieldsCont.fieldDefinitions.asSequence().map {
                                            gqlInputField: GraphQLInputObjectField ->
                                            gqlInputField.name
                                        }
                                    }
                                    .fold(::emptySequence, ::identity)
                                    .joinToString(", ", "{ ", " }")

                            val parentArgumentTypeName =
                                parentArgument.map { arg -> arg.type::class.qualifiedName }.orNull()
                            return GQLDataSourceException(
                                    GQLDataSourceErrorResponse.INVALID_INPUT,
                                    """parent_field_argument either does not have 
                                       |graphql_input_fields_container type--or--
                                       |does not contain child_input_object_field 
                                       |within it: [ expected: 
                                       |child_input_field.name: ${childInputObjectField.name}, 
                                       |actual: { parent_argument.type: $parentArgumentTypeName, 
                                       |parent_argument.
                                       |input_field_names_set $parentArgumentInputFieldsSet } ]
                                       |""".flattenIntoOneLine()
                                )
                                .failure()
                        }
                        val childInputObjectFieldPath =
                            parentPath.transform {
                                argument(
                                    parentArgument.orNull()!!.name,
                                    JsonNodeFactory.instance
                                        .objectNode()
                                        .putObject(childInputObjectFieldPathName.toString())
                                )
                            }
                        return DefaultGraphQLParameterInputObjectFieldAttribute(
                                sourcePath = childInputObjectFieldPath,
                                name = childInputObjectFieldConventionalName,
                                dataSourceLookupKey = key,
                                inputObjectField = childInputObjectField.some()
                            )
                            .successIfNonNull()
                    }
                    parentDirectiveArgument.isDefined() -> {
                        if (parentPath.directives.isEmpty() ||
                                !parentPath
                                    .directives
                                    .asIterable()
                                    .lastOrNone()
                                    .filter { (_, jsonValue) ->
                                        jsonValue.contains(parentDirectiveArgument.orNull()!!.name)
                                    }
                                    .isDefined()
                        ) {
                            return GQLDataSourceException(
                                    GQLDataSourceErrorResponse.INVALID_INPUT,
                                    """parent_path does not represent the 
                                       |parent_directive_argument: [ actual: ${parentPath}, 
                                       |expected: path with directive with 
                                       |parent_directive_argument_name 
                                       |[ name: ${parentDirectiveArgument.orNull()!!.name} ] ]
                                       |""".flattenIntoOneLine()
                                )
                                .failure()
                        }
                        if (!parentDirectiveArgument
                                .map { dirArg -> dirArg.type }
                                .flatMap { graphQLInputType: GraphQLInputType ->
                                    GraphQLInputFieldsContainerTypeExtractor.invoke(
                                        graphQLInputType
                                    )
                                }
                                .filter { graphQLInputFieldsContainer ->
                                    graphQLInputFieldsContainer.getFieldDefinition(
                                        childInputObjectField.name
                                    ) != null
                                }
                                .isDefined()
                        ) {
                            val parentDirectiveArgumentInputFieldsSet: String =
                                parentDirectiveArgument
                                    .flatMap { dirArg ->
                                        GraphQLInputFieldsContainerTypeExtractor.invoke(dirArg.type)
                                    }
                                    .map { gifc: GraphQLInputFieldsContainer ->
                                        gifc.fieldDefinitions.asSequence().map { giof -> giof.name }
                                    }
                                    .fold(::emptySequence, ::identity)
                                    .joinToString(", ", "{ ", " }")
                            val parentDirectiveArgumentTypeName: String? =
                                parentDirectiveArgument
                                    .flatMap { dirArg ->
                                        GraphQLInputFieldsContainerTypeExtractor.invoke(dirArg.type)
                                    }
                                    .map { gifc -> gifc::class.qualifiedName }
                                    .orNull()
                            return GQLDataSourceException(
                                    GQLDataSourceErrorResponse.INVALID_INPUT,
                                    """parent_directive_argument either is not an 
                                       |input_fields_container_type--or--
                                       |does not contain this child 
                                       |input_object_field if it is an 
                                       |input_fields_container type: 
                                       |[ expected: parent_directive_argument_input_fields_set: 
                                       |${parentDirectiveArgumentInputFieldsSet}, 
                                       |parent_directive_argument.type: ${parentDirectiveArgumentTypeName}, 
                                       |actual: { 
                                       |child_input_object_field.name: ${childInputObjectField.name} 
                                       |} ]
                                       |""".flattenIntoOneLine()
                                )
                                .failure()
                        }
                        val updatedDirectiveNameJsonValuePair =
                            parentPath
                                .directives
                                .asIterable()
                                .lastOrNone()
                                .filter { (_, jsonValue) ->
                                    jsonValue.has(parentDirectiveArgument.orNull()!!.name)
                                }
                                .map { (name, jsonValue) ->
                                    when (jsonValue) {
                                        is ObjectNode ->
                                            name to
                                                JsonNodeFactory.instance
                                                    .objectNode()
                                                    .setAll<ObjectNode>(jsonValue)
                                                    .set(
                                                        parentDirectiveArgument.orNull()!!.name,
                                                        JsonNodeFactory.instance
                                                            .objectNode()
                                                            .putNull(childInputObjectField.name)
                                                    )
                                        else -> {
                                            // Could instead throw error since this should be
                                            // an object_node type
                                            name to
                                                JsonNodeFactory.instance
                                                    .objectNode()
                                                    .set<ObjectNode>(
                                                        parentDirectiveArgument.orNull()!!.name,
                                                        JsonNodeFactory.instance
                                                            .objectNode()
                                                            .putNull(
                                                                childInputObjectFieldPathName
                                                                    .toString()
                                                            )
                                                    )
                                        }
                                    }
                                }
                                .successIfDefined()
                                .orElseThrow()
                        val childInputObjectFieldPath: SchematicPath =
                            parentPath.transform { directive(updatedDirectiveNameJsonValuePair) }
                        return DefaultGraphQLParameterInputObjectFieldAttribute(
                                dataSourceLookupKey = key,
                                sourcePath = childInputObjectFieldPath,
                                name = childInputObjectFieldConventionalName,
                                inputObjectField = childInputObjectField.some()
                            )
                            .successIfNonNull()
                    }
                    else -> {
                        return GQLDataSourceException(
                                GQLDataSourceErrorResponse.INVALID_INPUT,
                                """graphql_field_argument or graphql_directive_argument 
                                |must be defined in order to create a 
                                |parameter_attribute for an input_object_field 
                                |on one of them""".flattenIntoOneLine()
                            )
                            .failure()
                    }
                }
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

    override fun createParameterContainerTypeForParameterAttributeWithInputObjectValue(
        parameterAttribute: GraphQLParameterAttribute
    ): Try<GraphQLParameterContainerType> {
        return when (val inputObjectFieldsContainerType: GraphQLInputFieldsContainer? =
                GraphQLInputFieldsContainerTypeExtractor.invoke(parameterAttribute.dataType)
                    .orNull()
        ) {
            null -> {
                GQLDataSourceException(
                        GQLDataSourceErrorResponse.INVALID_INPUT,
                        """parameter_attribute.data_type must be of 
                        |${GraphQLInputFieldsContainer::class.qualifiedName} type 
                        |or must unwrap to such a type to be considered 
                        |as a graphql_parameter_container_type: 
                        |[ actual: ${parameterAttribute.dataType} ]
                        |""".flattenIntoOneLine()
                    )
                    .failure()
            }
            else -> {
                when {
                    parameterAttribute.isDirective() -> {
                        DefaultGraphQLParameterDirectiveContainerType(
                                dataSourceLookupKey = parameterAttribute.dataSourceLookupKey,
                                sourcePath = parameterAttribute.sourcePath,
                                directive = parameterAttribute.directive
                            )
                            .successIfNonNull()
                    }
                    parameterAttribute.isArgumentOnDirective() -> {
                        DefaultGraphQLParameterDirectiveArgumentContainerType(
                                dataSourceLookupKey = parameterAttribute.dataSourceLookupKey,
                                sourcePath = parameterAttribute.sourcePath,
                                name =
                                    StandardNamingConventions.PASCAL_CASE.deriveName(
                                        inputObjectFieldsContainerType.name
                                    ),
                                directiveArgument = parameterAttribute.directiveArgument
                            )
                            .successIfNonNull()
                    }
                    parameterAttribute.isArgumentOnFieldDefinition() -> {
                        DefaultGraphQLParameterFieldArgumentContainerType(
                                dataSourceLookupKey = parameterAttribute.dataSourceLookupKey,
                                sourcePath = parameterAttribute.sourcePath,
                                name =
                                    StandardNamingConventions.PASCAL_CASE.deriveName(
                                        inputObjectFieldsContainerType.name
                                    ),
                                fieldArgument = parameterAttribute.fieldArgument
                            )
                            .successIfNonNull()
                    }
                    parameterAttribute.isFieldOnInputObject() -> {
                        DefaultGraphQLParameterInputObjectContainerType(
                                dataSourceLookupKey = parameterAttribute.dataSourceLookupKey,
                                sourcePath = parameterAttribute.sourcePath,
                                name =
                                    StandardNamingConventions.PASCAL_CASE.deriveName(
                                        inputObjectFieldsContainerType.name
                                    ),
                                dataType = inputObjectFieldsContainerType as GraphQLInputObjectType
                            )
                            .successIfNonNull()
                    }
                    else -> {
                        GQLDataSourceException(
                                GQLDataSourceErrorResponse.UNEXPECTED_ERROR,
                                """unhandled parameter_attribute type; 
                                |unable to complete processing its 
                                |graphql_parameter_container_type representation: 
                                |[ graphql_parameter_attribute.type: 
                                |${parameterAttribute::class.qualifiedName} ]
                                |""".flattenIntoOneLine()
                            )
                            .failure()
                    }
                }
            }
        }
    }
}

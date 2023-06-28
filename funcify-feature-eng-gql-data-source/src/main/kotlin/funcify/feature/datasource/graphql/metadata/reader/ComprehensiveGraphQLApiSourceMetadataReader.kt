package funcify.feature.datasource.graphql.metadata.reader

import arrow.core.filterIsInstance
import arrow.core.getOrNone
import arrow.core.identity
import arrow.core.some
import arrow.core.toOption
import funcify.feature.datasource.graphql.error.GQLDataSourceErrorResponse
import funcify.feature.datasource.graphql.error.GQLDataSourceException
import funcify.feature.datasource.graphql.metadata.filter.GraphQLApiSourceMetadataFilter
import funcify.feature.datasource.graphql.metadata.reader.GraphQLSourceIndexCreationContext.DirectiveArgumentSourceIndexCreationContext
import funcify.feature.datasource.graphql.metadata.reader.GraphQLSourceIndexCreationContext.DirectiveSourceIndexCreationContext
import funcify.feature.datasource.graphql.metadata.reader.GraphQLSourceIndexCreationContext.FieldArgumentParameterSourceIndexCreationContext
import funcify.feature.datasource.graphql.metadata.reader.GraphQLSourceIndexCreationContext.FieldDefinitionSourceIndexCreationContext
import funcify.feature.datasource.graphql.metadata.reader.GraphQLSourceIndexCreationContext.InputObjectFieldSourceIndexCreationContext
import funcify.feature.datasource.graphql.metadata.reader.GraphQLSourceIndexCreationContext.InputObjectTypeSourceIndexCreationContext
import funcify.feature.datasource.graphql.metadata.reader.GraphQLSourceIndexCreationContext.OutputObjectTypeSourceIndexCreationContext
import funcify.feature.datasource.graphql.schema.GraphQLInputFieldsContainerTypeExtractor
import funcify.feature.datasource.graphql.schema.GraphQLOutputFieldsContainerTypeExtractor
import funcify.feature.datasource.graphql.schema.GraphQLParameterAttribute
import funcify.feature.datasource.graphql.schema.GraphQLParameterContainerType
import funcify.feature.datasource.graphql.schema.GraphQLSourceAttribute
import funcify.feature.datasource.graphql.schema.GraphQLSourceContainerType
import funcify.feature.datasource.graphql.schema.GraphQLSourceIndex
import funcify.feature.datasource.graphql.schema.GraphQLSourceIndexFactory
import funcify.feature.datasource.graphql.schema.GraphQLSourceMetamodel
import funcify.feature.schema.datasource.DataElementSource
import funcify.feature.schema.datasource.SourceMetamodel
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.control.RelationshipSpliterators
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.failure
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import funcify.feature.tools.extensions.TryExtensions.successIfNonNull
import graphql.schema.*
import java.util.stream.Stream
import java.util.stream.Stream.empty
import java.util.stream.StreamSupport
import kotlin.streams.asSequence
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.collections.immutable.toPersistentSet
import org.slf4j.Logger

/**
 *
 * @author smccarron
 * @created 2022-07-05
 */
internal class ComprehensiveGraphQLApiSourceMetadataReader(
    private val graphQLSourceIndexFactory: GraphQLSourceIndexFactory,
    private val graphQLSourceIndexCreationContextFactory: GraphQLSourceIndexCreationContextFactory,
    private val graphQLApiSourceMetadataFilter: GraphQLApiSourceMetadataFilter
) : GraphQLApiSourceMetadataReader {

    companion object {
        private val logger: Logger = loggerFor<ComprehensiveGraphQLApiSourceMetadataReader>()
    }

    override fun readSourceMetamodelFromMetadata(
        dataSourceKey: DataElementSource.Key<GraphQLSourceIndex>,
        metadataInput: GraphQLSchema,
    ): SourceMetamodel<GraphQLSourceIndex> {
        logger.debug(
            """read_source_container_types_from_metadata: 
                |[ metadata_input.query_type.name: ${metadataInput.queryType.name}, 
                | query_type.field_definitions.size: ${metadataInput.queryType.fieldDefinitions.size} ]
                |""".flatten()
        )
        if (metadataInput.queryType.fieldDefinitions.isEmpty()) {
            val message =
                """graphql_schema input for metadata on graphql 
                |source does not have any query type 
                |field definitions""".flatten()
            logger.error(
                """read_source_container_types_from_metadata: 
                |[ error: ${message} ]
                |""".flatten()
            )
            throw GQLDataSourceException(GQLDataSourceErrorResponse.INVALID_INPUT, message)
        }
        return traverseAcrossSchemaElementsBreadthFirstPairingParentAndChildElements(
            metadataInput.queryType
            )
            .asSequence()
            .fold(createRootQueryObjectTypeSourceIndexCreationContext(dataSourceKey, metadataInput)) {
                ctxCreationAttempt: Try<GraphQLSourceIndexCreationContext<*>>,
                (parent: GraphQLSchemaElement, child: GraphQLSchemaElement) ->
                ctxCreationAttempt.map { context ->
                    foldParentChildElementsIntoSourceIndexCreationContext(context, parent, child)
                }
            }
            .flatMap { sourceIndexCreationContext: GraphQLSourceIndexCreationContext<*> ->
                createSourceMetaModelFromLastSourceIndexCreationContext(sourceIndexCreationContext)
            }
            .orElseThrow()
    }

    private fun createRootQueryObjectTypeSourceIndexCreationContext(
        dataSourceKey: DataElementSource.Key<GraphQLSourceIndex>,
        input: GraphQLSchema,
    ): Try<GraphQLSourceIndexCreationContext<*>> {
        val rootContext: GraphQLSourceIndexCreationContext<GraphQLObjectType> =
            graphQLSourceIndexCreationContextFactory
                .createRootSourceIndexCreationContextForQueryGraphQLObjectType(
                    graphQLApiDataSourceKey = dataSourceKey,
                    graphQLObjectType = input.queryType
                )
        return graphQLSourceIndexFactory
            .createRootSourceContainerTypeForDataSourceKey(rootContext.graphQLApiDataSourceKey)
            .forGraphQLQueryObjectType(rootContext.currentElement)
            .map { rootSourceContainerType: GraphQLSourceContainerType ->
                rootContext.update { builder ->
                    builder.addOrUpdateGraphQLSourceIndex(rootSourceContainerType)
                }
            }
    }

    private fun traverseAcrossSchemaElementsBreadthFirstPairingParentAndChildElements(
        rootQueryObjectType: GraphQLObjectType
    ): Stream<Pair<GraphQLSchemaElement, GraphQLSchemaElement>> {

        val traversalFunction: (GraphQLSchemaElement) -> Stream<out GraphQLSchemaElement> =
            { parent: GraphQLSchemaElement ->
                when (parent) {
                    is GraphQLInputObjectField -> {
                        Stream.concat(
                            parent.appliedDirectives.stream(),
                            GraphQLInputFieldsContainerTypeExtractor.invoke(parent.type)
                                .map { graphQLInputFieldsContainer: GraphQLInputFieldsContainer ->
                                    Stream.of(graphQLInputFieldsContainer)
                                }
                                .fold(::empty, ::identity)
                        )
                    }
                    is GraphQLInputObjectType -> {
                        Stream.concat(
                            parent.appliedDirectives.stream(),
                            parent.fieldDefinitions.stream()
                        )
                    }
                    is GraphQLAppliedDirectiveArgument -> {
                        GraphQLInputFieldsContainerTypeExtractor.invoke(parent.type)
                            .map { graphQLInputFieldsContainer: GraphQLInputFieldsContainer ->
                                Stream.of(graphQLInputFieldsContainer)
                            }
                            .fold(::empty, ::identity)
                    }
                    is GraphQLAppliedDirective -> {
                        parent.arguments.stream()
                    }
                    is GraphQLArgument -> {
                        GraphQLInputFieldsContainerTypeExtractor.invoke(parent.type)
                            .map { graphQLInputFieldsContainer: GraphQLInputFieldsContainer ->
                                Stream.of(graphQLInputFieldsContainer)
                            }
                            .fold(::empty, ::identity)
                    }
                    is GraphQLFieldDefinition -> {
                        Stream.concat(
                            Stream.concat(
                                parent.appliedDirectives.stream(),
                                parent.arguments.stream()
                            ),
                            GraphQLOutputFieldsContainerTypeExtractor.invoke(parent.type)
                                .map { graphQLFieldsContainer: GraphQLFieldsContainer ->
                                    Stream.of(graphQLFieldsContainer)
                                }
                                .fold(::empty, ::identity)
                        )
                    }
                    is GraphQLObjectType -> {
                        Stream.concat(
                            parent.appliedDirectives.stream(),
                            parent.fieldDefinitions.stream().filter { fd ->
                                graphQLApiSourceMetadataFilter.includeGraphQLFieldDefinition(fd)
                            }
                        )
                    }
                    else -> {
                        Stream.empty()
                    }
                }
            }
        return StreamSupport.stream(
            RelationshipSpliterators.recursiveParentChildSpliterator(
                rootQueryObjectType,
                traversalFunction
            ),
            false
        )
    }
    private fun <E> foldParentChildElementsIntoSourceIndexCreationContext(
        previousContext: GraphQLSourceIndexCreationContext<E>,
        parent: GraphQLSchemaElement,
        child: GraphQLSchemaElement
    ): GraphQLSourceIndexCreationContext<*> where E : GraphQLSchemaElement {
        val nameAndTypeStringifier = nameAndTypePairStringifier()
        val parentNameAndType: String = nameAndTypeStringifier.invoke(parent)
        val childNameAndType: String = nameAndTypeStringifier.invoke(child)
        logger.debug(
            """fold_parent_child_elements_into_source_index_creation_context: 
               |[ parent: ${parentNameAndType}, 
               |child: $childNameAndType ]
               |""".flatten()
        )
        val parentPath: SchematicPath =
            previousContext.schematicPathCreatedBySchemaElement[parent]
                .toOption()
                .successIfDefined {
                    val nameAndTypeStr = nameAndTypePairStringifier().invoke(parent)
                    GQLDataSourceException(
                        GQLDataSourceErrorResponse.UNEXPECTED_ERROR,
                        """parent_element must have corresponding path 
                           |and source_index in previous_context: 
                           |[ $nameAndTypeStr ] 
                           |entries may be being processed out-of-order 
                           |and behavior of source_index_creation thus 
                           |is not guaranteed to capture all pertinent 
                           |schema metadata
                           |""".flatten()
                    )
                }
                .orElseThrow()

        /**
         * Make this only point where context is transitioned from previous child element to next
         * child element
         */
        val context: GraphQLSourceIndexCreationContext<*> =
            previousContext.update { builder -> builder.nextSchemaElement(parentPath, child) }
        return when (context) {
            is OutputObjectTypeSourceIndexCreationContext -> {
                // Case 1: The root container type, typically Query, has already been created;
                // for source container types not corresponding to root, this output_object_type
                // must belong to the type (possibly nested e.g.
                // (if something like [MyContainerType!]! => non-null( list( non-null(
                // MyContainerType ) ) ) )
                // on an existing graphql_field_definition on an existing
                // graphql_source_attribute
                val correspondingSourceAttribute: GraphQLSourceAttribute =
                    context.sourceAttributeWithSameOutputObjectTypeAndPath
                        .successIfDefined {
                            GQLDataSourceException(
                                GQLDataSourceErrorResponse.UNEXPECTED_ERROR,
                                """current_element should already have  
                                   |field_definition with same (possibly nested) output 
                                   |object type and parent path defined 
                                   |if processing order is correct: 
                                   |[ ${nameAndTypePairStringifier().invoke(child)} ]
                                   |""".flatten()
                            )
                        }
                        .orElseThrow()
                graphQLSourceIndexFactory
                    .createSourceContainerTypeForDataSourceKey(context.graphQLApiDataSourceKey)
                    .forAttributePathAndDefinition(
                        correspondingSourceAttribute.sourcePath,
                        correspondingSourceAttribute.graphQLFieldDefinition
                    )
                    .map { gqlsct ->
                        context.update { builder -> builder.addOrUpdateGraphQLSourceIndex(gqlsct) }
                    }
                    .orElseThrow()
            }
            is FieldDefinitionSourceIndexCreationContext -> {
                // Case 2: All field_definitions must have a parent path; for field_definitions
                // whose parent is root, the parent_path is the root_path
                val graphQLSourceAttribute: GraphQLSourceAttribute =
                    when {
                        // Parent is root and child does not already have source_attribute
                        SchematicPath.getRootPath() == parentPath &&
                            context.parentOutputObjectType.isDefined() -> {
                            graphQLSourceIndexFactory
                                .createSourceAttributeForDataSourceKey(
                                    context.graphQLApiDataSourceKey
                                )
                                .withParentRootContainerType(
                                    context.parentOutputObjectType.orNull()!!
                                )
                                .forChildAttributeDefinition(context.currentElement)
                        }
                        else -> {
                            // Parent is not root but parent field_definition should be a
                            // container_type in which this child_field_definition is present
                            context.parentFieldDefinition
                                .successIfDefined {
                                    GQLDataSourceException(
                                        GQLDataSourceErrorResponse.UNEXPECTED_ERROR,
                                        """current_element should already have parent 
                                           |field_definition defined if processing order is correct: 
                                           |[ ${nameAndTypePairStringifier().invoke(child)} ]
                                           |""".flatten()
                                    )
                                }
                                .flatMap { parentFieldDef ->
                                    graphQLSourceIndexFactory
                                        .createSourceAttributeForDataSourceKey(
                                            context.graphQLApiDataSourceKey
                                        )
                                        .withParentPathAndDefinition(parentPath, parentFieldDef)
                                        .forChildAttributeDefinition(context.currentElement)
                                }
                        }
                    }.orElseThrow()
                context.update { builder ->
                    builder.addOrUpdateGraphQLSourceIndex(graphQLSourceAttribute)
                }
            }
            is FieldArgumentParameterSourceIndexCreationContext -> {
                val parentFieldDefinition: GraphQLFieldDefinition =
                    context.parentFieldDefinition
                        .successIfDefined {
                            GQLDataSourceException(
                                GQLDataSourceErrorResponse.UNEXPECTED_ERROR,
                                """parent_schema_element of field_argument 
                                   |must be a graphql_field_definition: 
                                   |[ actual_type: ${parent::class.simpleName} ]
                                   |""".flatten()
                            )
                        }
                        .orElseThrow()
                graphQLSourceIndexFactory
                    .createParameterAttributeForDataSourceKey(context.graphQLApiDataSourceKey)
                    .withParentPathAndFieldDefinition(parentPath, parentFieldDefinition)
                    .forChildArgument(context.currentElement)
                    .map { graphQLParameterAttribute: GraphQLParameterAttribute ->
                        context.update { builder ->
                            builder.addOrUpdateGraphQLSourceIndex(graphQLParameterAttribute)
                        }
                    }
                    .orElseThrow()
            }
            is DirectiveSourceIndexCreationContext -> {
                when {
                        context.parentFieldDefinition.isDefined() -> {
                            graphQLSourceIndexFactory
                                .createParameterAttributeForDataSourceKey(
                                    context.graphQLApiDataSourceKey
                                )
                                .forAppliedDirective(context.currentElement)
                                .onParentDefinition(
                                    parentPath,
                                    context.parentFieldDefinition.orNull()!!
                                )
                                .zip(
                                    graphQLSourceIndexFactory
                                        .createParameterContainerTypeForDataSourceKey(
                                            context.graphQLApiDataSourceKey
                                        )
                                        .forAppliedDirective(context.currentElement)
                                        .onParentDefinition(
                                            parentPath,
                                            context.parentFieldDefinition.orNull()!!
                                        )
                                )
                        }
                        context.parentArgument.isDefined() -> {
                            graphQLSourceIndexFactory
                                .createParameterAttributeForDataSourceKey(
                                    context.graphQLApiDataSourceKey
                                )
                                .forAppliedDirective(context.currentElement)
                                .onParentDefinition(parentPath, context.parentArgument.orNull()!!)
                                .zip(
                                    graphQLSourceIndexFactory
                                        .createParameterContainerTypeForDataSourceKey(
                                            context.graphQLApiDataSourceKey
                                        )
                                        .forAppliedDirective(context.currentElement)
                                        .onParentDefinition(
                                            parentPath,
                                            context.parentArgument.orNull()!!
                                        )
                                )
                        }
                        context.parentInputObjectField.isDefined() -> {
                            graphQLSourceIndexFactory
                                .createParameterAttributeForDataSourceKey(
                                    context.graphQLApiDataSourceKey
                                )
                                .forAppliedDirective(context.currentElement)
                                .onParentDefinition(
                                    parentPath,
                                    context.parentInputObjectField.orNull()!!
                                )
                                .zip(
                                    graphQLSourceIndexFactory
                                        .createParameterContainerTypeForDataSourceKey(
                                            context.graphQLApiDataSourceKey
                                        )
                                        .forAppliedDirective(context.currentElement)
                                        .onParentDefinition(
                                            parentPath,
                                            context.parentInputObjectField.orNull()!!
                                        )
                                )
                        }
                        else -> {
                            GQLDataSourceException(
                                    GQLDataSourceErrorResponse.GRAPHQL_DATA_SOURCE_CREATION_ERROR,
                                    """unhandled_applied_directive_case: 
                                       |[ parent_element.type: ${parent::class.simpleName}, 
                                       |child_directive.name: ${context.currentElement.name} 
                                       |]""".flatten()
                                )
                                .failure()
                        }
                    }
                    .map { (paramAttr, paramContType) ->
                        context.update { builder ->
                            builder
                                .addOrUpdateGraphQLSourceIndex(paramAttr)
                                .addOrUpdateGraphQLSourceIndex(paramContType)
                        }
                    }
                    .peekIfFailure { t: Throwable ->
                        logger.warn(
                            """directive_source_index_creation_context: 
                            |[ status: failed but ignoring error ] 
                            |[ type: ${t::class.simpleName}, message: ${t.message} ]
                            |""".flatten(),
                            t
                        )
                    }
                    .orElse(context)
            }
            is DirectiveArgumentSourceIndexCreationContext -> {
                val parentDirective: GraphQLAppliedDirective =
                    context.parentDirective
                        .successIfDefined {
                            GQLDataSourceException(
                                GQLDataSourceErrorResponse.UNEXPECTED_ERROR,
                                """parent_schema_element of applied_directive_argument 
                                   |must be a applied_directive: 
                                   |[ actual_type: ${parent::class.simpleName} ]
                                   |""".flatten()
                            )
                        }
                        .orElseThrow()
                graphQLSourceIndexFactory
                    .createParameterAttributeForDataSourceKey(context.graphQLApiDataSourceKey)
                    .withParentPathAndAppliedDirective(parentPath, parentDirective)
                    .forChildDirectiveArgument(context.currentElement)
                    .map { graphQLParameterAttribute: GraphQLParameterAttribute ->
                        context.update { builder ->
                            builder.addOrUpdateGraphQLSourceIndex(graphQLParameterAttribute)
                        }
                    }
                    .orElseThrow()
            }
            is InputObjectTypeSourceIndexCreationContext -> {
                context.parentParameterAttribute
                    .successIfDefined {
                        GQLDataSourceException(
                            GQLDataSourceErrorResponse.UNEXPECTED_ERROR,
                            """parameter_attribute for element 
                               |${nameAndTypePairStringifier().invoke(parent)} 
                               |should have been processed before its 
                               |input_object_type container_type 
                               |index child""".flatten()
                        )
                    }
                    .flatMap { parentParamAttr ->
                        graphQLSourceIndexFactory
                            .createParameterContainerTypeForParameterAttributeWithInputObjectValue(
                                parentParamAttr
                            )
                    }
                    .map { paramContType ->
                        context.update { builder ->
                            builder.addOrUpdateGraphQLSourceIndex(paramContType)
                        }
                    }
                    .orElseThrow()
            }
            is InputObjectFieldSourceIndexCreationContext -> {
                context.parentParameterAttribute
                    .successIfDefined()
                    .flatMap { ppa: GraphQLParameterAttribute ->
                        when {
                            ppa.isArgumentOnFieldDefinition() -> {
                                graphQLSourceIndexFactory
                                    .createParameterAttributeForDataSourceKey(
                                        context.graphQLApiDataSourceKey
                                    )
                                    .withParentPathAndFieldArgument(
                                        ppa.sourcePath,
                                        ppa.fieldArgument.orNull()!!
                                    )
                                    .forInputObjectField(context.currentElement)
                            }
                            ppa.isArgumentOnDirective() -> {
                                graphQLSourceIndexFactory
                                    .createParameterAttributeForDataSourceKey(
                                        context.graphQLApiDataSourceKey
                                    )
                                    .withParentPathAndDirectiveArgument(
                                        ppa.sourcePath,
                                        ppa.directiveArgument.orNull()!!
                                    )
                                    .forInputObjectField(context.currentElement)
                            }
                            ppa.isFieldOnInputObject() -> {
                                graphQLSourceIndexFactory
                                    .createParameterAttributeForDataSourceKey(
                                        context.graphQLApiDataSourceKey
                                    )
                                    .withParentPathAndInputObjectType(
                                        ppa.sourcePath,
                                        context.parentInputObjectType.orNull()!!
                                    )
                                    .forInputObjectField(context.currentElement)
                            }
                            else -> {
                                GQLDataSourceException(
                                        GQLDataSourceErrorResponse.UNEXPECTED_ERROR,
                                        """unhandled type of parent_parameter_attribute: 
                                           |[ actual: ${ppa::class.simpleName} ]
                                           |""".flatten()
                                    )
                                    .failure()
                            }
                        }
                    }
                    .map { gqlpa: GraphQLParameterAttribute ->
                        context.update { builder -> builder.addOrUpdateGraphQLSourceIndex(gqlpa) }
                    }
                    .orElseThrow()
            }
            else -> {
                context
            }
        }
    }

    private fun nameAndTypePairStringifier() = { element: GraphQLSchemaElement ->
        element
            .some()
            .filterIsInstance<GraphQLNamedSchemaElement>()
            .map { e -> mapOf("name" to e.name, "type" to e::class.simpleName) }
            .map { strMap ->
                strMap
                    .asSequence()
                    .joinToString(
                        separator = ", ",
                        prefix = "{ ",
                        postfix = " }",
                        transform = { (k, v) -> "$k: $v" }
                    )
            }
            .orNull()
            ?: "<NA>"
    }

    private fun <E : GraphQLSchemaElement> createSourceMetaModelFromLastSourceIndexCreationContext(
        graphQLSourceIndexCreationContext: GraphQLSourceIndexCreationContext<E>
    ): Try<SourceMetamodel<GraphQLSourceIndex>> {
        logger.debug(
            """create_source_metamodel_from_last_source_index_creation_context: 
            |[ creation_context.graphql_source_container_types.size: 
            |${graphQLSourceIndexCreationContext.graphqlSourceContainerTypesBySchematicPath.size}, 
            |creation_context.graphql_source_attributes.size: 
            |${graphQLSourceIndexCreationContext.graphqlParameterAttributesBySchematicPath.size} 
            |]""".flatten()
        )
        val updatedGraphQLSourceContainerTypesByPathAttempt:
            Try<PersistentMap<SchematicPath, GraphQLSourceContainerType>> =
            graphQLSourceIndexCreationContext.graphqlSourceAttributesBySchematicPath
                .asSequence()
                .flatMap { (path, sa) ->
                    path.getParentPath().fold(::emptySequence, ::sequenceOf).map { pp -> pp to sa }
                }
                .groupBy({ (path, _) -> path }, { (_, sa) -> sa })
                .asSequence()
                .fold(
                    graphQLSourceIndexCreationContext.graphqlSourceContainerTypesBySchematicPath
                        .toPersistentMap()
                        .successIfNonNull()
                ) {
                    sctByPathUpdateAttempt:
                        Try<PersistentMap<SchematicPath, GraphQLSourceContainerType>>,
                    (parentPath: SchematicPath, sourceAttrs: List<GraphQLSourceAttribute>) ->
                    sctByPathUpdateAttempt.map { sctByPath ->
                        sctByPath
                            .getOrNone(parentPath)
                            .fold(
                                {
                                    val sourceAttributeNamesSet: String =
                                        sourceAttrs
                                            .asSequence()
                                            .map { sa -> sa.name.toString() }
                                            .joinToString(", ", " }", " }")
                                    throw GQLDataSourceException(
                                        GQLDataSourceErrorResponse.UNEXPECTED_ERROR,
                                        """graphql_source_attributes are missing a 
                                        |missing graphql_source_container_type entry: 
                                        |[ parent_path: ${parentPath}, 
                                        |source_attributes.names: $sourceAttributeNamesSet ]
                                        |""".flatten()
                                    )
                                },
                                { gsct: GraphQLSourceContainerType ->
                                    graphQLSourceIndexFactory
                                        .updateSourceContainerType(gsct)
                                        .withChildSourceAttributes(sourceAttrs.toPersistentSet())
                                        .map { updatedGSCT: GraphQLSourceContainerType ->
                                            sctByPath.put(parentPath, updatedGSCT)
                                        }
                                        .orElseThrow()
                                }
                            )
                    }
                }
        val updatedParameterContainerTypesByPathAttempt:
            Try<PersistentMap<SchematicPath, GraphQLParameterContainerType>> =
            graphQLSourceIndexCreationContext.graphqlParameterAttributesBySchematicPath
                .asSequence()
                .flatMap { (path, attr) ->
                    path.getParentPath().fold(::emptySequence, ::sequenceOf).map { pp ->
                        pp to attr
                    }
                }
                .groupBy({ (path, _) -> path }, { (_, attr) -> attr })
                .asSequence()
                .fold(
                    graphQLSourceIndexCreationContext.graphqlParameterContainerTypesBySchematicPath
                        .toPersistentMap()
                        .successIfNonNull()
                ) {
                    pctByPathAttempt:
                        Try<PersistentMap<SchematicPath, GraphQLParameterContainerType>>,
                    (parentPath: SchematicPath, paramAttrs: List<GraphQLParameterAttribute>) ->
                    pctByPathAttempt.map { pctByPath ->
                        pctByPath
                            .getOrNone(parentPath)
                            .fold(
                                { pctByPath },
                                { pct: GraphQLParameterContainerType ->
                                    graphQLSourceIndexFactory
                                        .updateParameterContainerType(pct)
                                        .withChildParameterAttributes(paramAttrs.toPersistentSet())
                                        .map { updatedGPCT ->
                                            pctByPath.put(parentPath, updatedGPCT)
                                        }
                                        .orElseThrow()
                                }
                            )
                    }
                }
        return updatedGraphQLSourceContainerTypesByPathAttempt
            .zip(updatedParameterContainerTypesByPathAttempt)
            .map { (updatedSourceContainers, updatedParameterContainers) ->
                sequenceOf<ImmutableMap<SchematicPath, GraphQLSourceIndex>>(
                        updatedSourceContainers,
                        graphQLSourceIndexCreationContext.graphqlSourceAttributesBySchematicPath,
                        updatedParameterContainers,
                        graphQLSourceIndexCreationContext.graphqlParameterAttributesBySchematicPath
                    )
                    .flatMap { sourceIndexByPath -> sourceIndexByPath.asSequence() }
                    .fold(persistentMapOf<SchematicPath, PersistentSet<GraphQLSourceIndex>>()) {
                        pm,
                        (path, sourceIndex) ->
                        pm.put(path, pm.getOrDefault(path, persistentSetOf()).add(sourceIndex))
                    }
            }
            .map { sourceIndicesByPath ->
                GraphQLSourceMetamodel(sourceIndicesByPath = sourceIndicesByPath)
            }
    }
}

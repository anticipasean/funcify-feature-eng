package funcify.feature.datasource.graphql.sdl

import arrow.core.Either
import arrow.core.Option
import arrow.core.left
import arrow.core.none
import arrow.core.right
import arrow.core.some
import funcify.feature.datasource.error.DataSourceErrorResponse
import funcify.feature.datasource.error.DataSourceException
import funcify.feature.datasource.graphql.error.GQLDataSourceErrorResponse
import funcify.feature.datasource.graphql.error.GQLDataSourceException
import funcify.feature.datasource.graphql.schema.GraphQLSourceAttribute
import funcify.feature.datasource.graphql.schema.GraphQLSourceContainerType
import funcify.feature.datasource.graphql.schema.GraphQLSourceIndex
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext.ParameterJunctionVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext.ParameterLeafVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext.SourceJunctionVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext.SourceLeafVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionCreationContext.SourceRootVertexSDLDefinitionCreationContext
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionImplementationStrategy
import funcify.feature.datasource.sdl.impl.DataSourceIndexTypeBasedSDLDefinitionStrategy
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.index.CompositeParameterContainerType
import funcify.feature.schema.index.CompositeSourceAttribute
import funcify.feature.schema.index.CompositeSourceContainerType
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.control.TraversalFunctions
import funcify.feature.tools.extensions.FunctionExtensions.compose
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import graphql.language.Description
import graphql.language.FieldDefinition
import graphql.language.ListType
import graphql.language.Node
import graphql.language.NonNullType
import graphql.language.ObjectTypeDefinition
import graphql.language.SourceLocation
import graphql.language.Type
import graphql.language.TypeName
import graphql.schema.GraphQLFieldsContainer
import graphql.schema.GraphQLInputType
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNamedType
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLOutputType
import graphql.schema.GraphQLType
import kotlin.reflect.full.isSubclassOf
import org.slf4j.Logger

/**
 *
 * @author smccarron
 * @created 2022-07-02
 */
class GraphQLSourceIndexBasedSDLDefinitionImplementationStrategy :
    DataSourceIndexTypeBasedSDLDefinitionStrategy<
        GraphQLSourceIndex, SchematicVertexSDLDefinitionCreationContext<*>>(
        GraphQLSourceIndex::class
    ),
    SchematicVertexSDLDefinitionImplementationStrategy {

    companion object {
        private val logger: Logger =
            loggerFor<GraphQLSourceIndexBasedSDLDefinitionImplementationStrategy>()
    }

    override fun applyToContext(
        context: SchematicVertexSDLDefinitionCreationContext<*>
    ): Try<SchematicVertexSDLDefinitionCreationContext<*>> {
        logger.debug("apply_to_context: [ context.path: ${context.path} ]")
        return when (context) {
            is SourceRootVertexSDLDefinitionCreationContext -> {
                val compositeSourceContainerType: CompositeSourceContainerType =
                    context.compositeSourceContainerType
                createObjectTypeDefinitionForGraphQLSourceContainerType(
                        compositeSourceContainerType,
                        context
                    )
                    .map { objectTypeDef ->
                        context.update {
                            addSDLDefinitionForSchematicPath(context.path, objectTypeDef)
                        }
                    }
            }
            is SourceJunctionVertexSDLDefinitionCreationContext -> {
                val compositeSourceContainerType: CompositeSourceContainerType =
                    context.compositeSourceContainerType
                val compositeSourceAttribute: CompositeSourceAttribute =
                    context.compositeSourceAttribute
                createObjectTypeDefinitionForGraphQLSourceContainerType(
                        compositeSourceContainerType,
                        context
                    )
                    .zip(
                        createFieldDefinitionForGraphQLSourceAttributeType(
                            compositeSourceAttribute,
                            context
                        )
                    )
                    .map { (objectTypeDef, fieldDef) ->
                        context.update {
                            addSDLDefinitionForSchematicPath(context.path, objectTypeDef)
                                .addSDLDefinitionForSchematicPath(context.path, fieldDef)
                        }
                    }
            }
            is SourceLeafVertexSDLDefinitionCreationContext -> {
                val compositeSourceAttribute: CompositeSourceAttribute =
                    context.compositeSourceAttribute
                createFieldDefinitionForGraphQLSourceAttributeType(
                        compositeSourceAttribute,
                        context
                    )
                    .map { fieldDef ->
                        context.update { addSDLDefinitionForSchematicPath(context.path, fieldDef) }
                    }
            }
            is ParameterJunctionVertexSDLDefinitionCreationContext -> {


                TODO("parameter_junction_vertex_sdl_definition_creation_context not yet handled")
            }
            is ParameterLeafVertexSDLDefinitionCreationContext -> {
                TODO("parameter_leaf_vertex_sdl_definition_creation_context not yet handled")
            }
        }
    }

    private fun createObjectTypeDefinitionForGraphQLSourceContainerType(
        compositeSourceContainerType: CompositeSourceContainerType,
        context: SchematicVertexSDLDefinitionCreationContext<*>,
    ): Try<ObjectTypeDefinition> {
        val graphQLSourceContainerTypes: List<GraphQLSourceContainerType> =
            compositeSourceContainerType
                .getSourceContainerTypeByDataSource()
                .keys
                .asSequence()
                .filter { dsKey -> dsKey.sourceIndexType.isSubclassOf(GraphQLSourceIndex::class) }
                .map { dsKey ->
                    compositeSourceContainerType.getSourceContainerTypeByDataSource()[dsKey]!!
                }
                .filterIsInstance<GraphQLSourceContainerType>()
                .toList()
        return when (graphQLSourceContainerTypes.size) {
            0 -> {
                val sourceContainerTypeDataSourceKeysSet =
                    compositeSourceContainerType
                        .getSourceContainerTypeByDataSource()
                        .keys
                        .joinToString(", ", "{ ", " }")
                Try.failure<GraphQLSourceContainerType>(
                    DataSourceException(
                        DataSourceErrorResponse.STRATEGY_INCORRECTLY_APPLIED,
                        """expected at least one graphql_data_source 
                           |for vertex in context [ vertex.path: ${context.path}, 
                           |vertex.composite_container_type
                           |.data_source_keys: $sourceContainerTypeDataSourceKeysSet 
                           |]""".flattenIntoOneLine()
                    )
                )
            }
            1 -> {
                Try.success(graphQLSourceContainerTypes.first())
            }
            else -> {
                val graphQLSourceContainerTypesFoundSet =
                    graphQLSourceContainerTypes
                        .map { gqlsct -> gqlsct.dataSourceLookupKey }
                        .joinToString(", ", "{ ", " }")
                Try.failure<GraphQLSourceContainerType>(
                    GQLDataSourceException(
                        GQLDataSourceErrorResponse.SCHEMA_CREATION_ERROR,
                        """more than one gql_data_source provides 
                           |values for container_type [ container_type.path: 
                           |${graphQLSourceContainerTypes.first().sourcePath}, 
                           |gql_data_sources: $graphQLSourceContainerTypesFoundSet ] 
                           |handling for multiple sources for same container_type 
                           |undefined""".flattenIntoOneLine()
                    )
                )
            }
        }.map { graphQLSourceContainerType: GraphQLSourceContainerType ->
            deriveObjectTypeDefinitionFromGraphQLApiSourceContainerType(graphQLSourceContainerType)
        }
    }

    private fun createFieldDefinitionForGraphQLSourceAttributeType(
        compositeSourceAttribute: CompositeSourceAttribute,
        context: SchematicVertexSDLDefinitionCreationContext<*>,
    ): Try<FieldDefinition> {
        val graphQLSourceAttributes: List<GraphQLSourceAttribute> =
            compositeSourceAttribute
                .getSourceAttributeByDataSource()
                .keys
                .asSequence()
                .filter { dsKey -> dsKey.sourceIndexType.isSubclassOf(GraphQLSourceIndex::class) }
                .filterIsInstance<DataSource.Key<GraphQLSourceIndex>>()
                .map { dsKey ->
                    compositeSourceAttribute.getSourceAttributeForDataSourceKey<
                        GraphQLSourceIndex, GraphQLSourceAttribute>(dsKey)
                }
                .filterIsInstance<GraphQLSourceAttribute>()
                .toList()
        return when (graphQLSourceAttributes.size) {
            0 -> {
                val sourceAttributeDataSourceKetSet =
                    compositeSourceAttribute
                        .getSourceAttributeByDataSource()
                        .keys
                        .joinToString(", ", "{ ", " }")
                Try.failure<GraphQLSourceAttribute>(
                    DataSourceException(
                        DataSourceErrorResponse.STRATEGY_INCORRECTLY_APPLIED,
                        """expected at least one gql_data_source 
                           |for vertex in context [ vertex.path: ${context.path}, 
                           |vertex.composite_attribute
                           |.data_source_keys: $sourceAttributeDataSourceKetSet 
                           |]""".flattenIntoOneLine()
                    )
                )
            }
            1 -> {
                Try.success(graphQLSourceAttributes.first())
            }
            else -> {
                val graphQLSourceContainerTypesFoundSet =
                    graphQLSourceAttributes
                        .map { gqlsct -> gqlsct.dataSourceLookupKey }
                        .joinToString(", ", "{ ", " }")
                Try.failure<GraphQLSourceAttribute>(
                    GQLDataSourceException(
                        GQLDataSourceErrorResponse.SCHEMA_CREATION_ERROR,
                        """more than one gql_data_source provides 
                           |values for attribute [ attribute.path: 
                           |${graphQLSourceAttributes.first().sourcePath}, 
                           |gql_data_sources: $graphQLSourceContainerTypesFoundSet ] 
                           |handling for multiple sources for same container_type 
                           |undefined""".flattenIntoOneLine()
                    )
                )
            }
        }.map { graphQLSourceAttribute: GraphQLSourceAttribute ->
            deriveFieldDefinitionFromGraphQLSourceAttribute(graphQLSourceAttribute)
        }
    }

    private fun deriveObjectTypeDefinitionFromGraphQLApiSourceContainerType(
        graphQLSourceContainerType: GraphQLSourceContainerType
    ): ObjectTypeDefinition {
        val graphQLFieldsContainerType: GraphQLFieldsContainer =
            Try.attempt { graphQLSourceContainerType.graphQLFieldsContainerType }.orElseThrow()
        return when (val fieldsContainerDefinition: Node<Node<*>>? =
                graphQLFieldsContainerType.definition
        ) { //
            // null if fieldsContainerType was not based on an SDL definition
            null -> {
                if (graphQLFieldsContainerType.description?.isNotEmpty() == true) {
                    ObjectTypeDefinition.newObjectTypeDefinition()
                        .name(graphQLFieldsContainerType.name)
                        .description(
                            Description(
                                graphQLFieldsContainerType.description ?: "",
                                SourceLocation.EMPTY,
                                graphQLFieldsContainerType.description?.contains('\n') ?: false
                            )
                        )
                        /* Let attribute vertices be added separately
                         * .fieldDefinitions(
                         *    graphQLFieldsContainerType
                         *        .fieldDefinitions
                         *        .stream()
                         *        .map { fd -> fd.definition.toOption() }
                         *        .flatMapOptions()
                         *        .reduceToPersistentList()
                         *)
                         */
                        .build()
                } else {
                    ObjectTypeDefinition.newObjectTypeDefinition()
                        .name(graphQLFieldsContainerType.name)
                        /* Let attribute vertices be added separately
                         * .fieldDefinitions(
                         *     graphQLFieldsContainerType
                         *         .fieldDefinitions
                         *         .stream()
                         *         .map { fd -> fd.definition.toOption() }
                         *         .flatMapOptions()
                         *         .reduceToPersistentList()
                         * )
                         */
                        .build()
                }
            } //
            // only object_type_definition if fieldsContainerType is actually a GraphQLObjectType
            is ObjectTypeDefinition -> {
                fieldsContainerDefinition.transform { builder: ObjectTypeDefinition.Builder ->
                    /*
                     * Clear list of field_definitions already available in given object_type_definition
                     * Let this list be updated as source_attributes are updated
                     */
                    builder.fieldDefinitions(listOf())
                }
            }
            else -> {
                throw GQLDataSourceException(
                    GQLDataSourceErrorResponse.SCHEMA_CREATION_ERROR,
                    """graphql_fields_container_type does not conform to expected 
                      |contract for get_definition: [ graphql_field_container_type.definition.type: 
                      |${fieldsContainerDefinition::class.qualifiedName} 
                      |]""".flattenIntoOneLine()
                )
            }
        }
    }

    private fun deriveFieldDefinitionFromGraphQLSourceAttribute(
        graphQLSourceAttribute: GraphQLSourceAttribute
    ): FieldDefinition {
        return when (val sdlDefinition: FieldDefinition? =
                graphQLSourceAttribute.graphQLFieldDefinition.definition
        ) {
            null -> {
                if (graphQLSourceAttribute.graphQLFieldDefinition.description?.isNotEmpty() == true
                ) {
                    FieldDefinition.newFieldDefinition()
                        .name(graphQLSourceAttribute.graphQLFieldDefinition.name)
                        .description(
                            Description(
                                graphQLSourceAttribute.graphQLFieldDefinition.description,
                                SourceLocation.EMPTY,
                                graphQLSourceAttribute.graphQLFieldDefinition.description?.contains(
                                    '\n'
                                                                                                   )
                                    ?: false
                            )
                        )
                        .type(
                            recursivelyDetermineSDLTypeForGraphQLInputOrOutputType(
                                graphQLInputOrOutputType =
                                    graphQLSourceAttribute.graphQLFieldDefinition.type
                            )
                        )
                        .build()
                } else {
                    FieldDefinition.newFieldDefinition()
                        .name(graphQLSourceAttribute.graphQLFieldDefinition.name)
                        .type(
                            recursivelyDetermineSDLTypeForGraphQLInputOrOutputType(
                                graphQLInputOrOutputType =
                                    graphQLSourceAttribute.graphQLFieldDefinition.type
                            )
                        )
                        .build()
                }
            }
            else -> {
                sdlDefinition
            }
        }
    }

    private fun recursivelyDetermineSDLTypeForGraphQLInputOrOutputType(
        graphQLInputOrOutputType: GraphQLType
    ): Type<*> {
        if (graphQLInputOrOutputType !is GraphQLOutputType &&
                graphQLInputOrOutputType !is GraphQLInputType
        ) {
            val message =
                """the graphql_type passed in as input is 
                   |neither an input or output type 
                   |so an SDL Type<*> instance cannot be determined: 
                   |[ actual: ${graphQLInputOrOutputType::class.qualifiedName} 
                   |]""".flattenIntoOneLine()
            throw GQLDataSourceException(GQLDataSourceErrorResponse.SCHEMA_CREATION_ERROR, message)
        }
        val traversalFunction:
            (Pair<(Type<*>) -> Type<*>, GraphQLType>) -> Option<
                    Either<Pair<(Type<*>) -> Type<*>, GraphQLType>, Type<*>>> =
            { pair: Pair<(Type<*>) -> Type<*>, GraphQLType> ->
                when (val graphQLType: GraphQLType = pair.second) {
                    is GraphQLNonNull ->
                        (pair.first.compose<Type<*>, Type<*>, Type<*>> { t: Type<*> ->
                                NonNullType.newNonNullType().type(t).build()
                            } to graphQLType.wrappedType)
                            .left()
                            .some()
                    is GraphQLList ->
                        (pair.first.compose<Type<*>, Type<*>, Type<*>> { t: Type<*> ->
                                ListType.newListType().type(t).build()
                            } to graphQLType.wrappedType)
                            .left()
                            .some()
                    is GraphQLNamedType ->
                        pair.first
                            .invoke(TypeName.newTypeName(graphQLType.name).build())
                            .right()
                            .some()
                    else -> none()
                }
            }
        return TraversalFunctions.recurseWithOption(
                ({ t: Type<*> -> t } to graphQLInputOrOutputType),
                traversalFunction
            )
            .fold(
                { ->
                    throw unnamedGraphQLInputOrOutputTypeGraphQLSchemaCreationError(
                        graphQLInputOrOutputType
                    )
                },
                { t: Type<*> -> t }
            )
    }

    private fun unnamedGraphQLInputOrOutputTypeGraphQLSchemaCreationError(
        graphQLInputOrOutputType: GraphQLType
    ): GQLDataSourceException {
        val inputOrOutputType: String =
            if (graphQLInputOrOutputType is GraphQLInputType) {
                "input_type"
            } else {
                "output_type"
            }
        return GQLDataSourceException(
            GQLDataSourceErrorResponse.SCHEMA_CREATION_ERROR,
            """graphql_field_definition.${inputOrOutputType} [ type.to_string: 
                |$graphQLInputOrOutputType ] 
                |does not have name for use in SDL type creation
                |""".flattenIntoOneLine()
        )
    }
}

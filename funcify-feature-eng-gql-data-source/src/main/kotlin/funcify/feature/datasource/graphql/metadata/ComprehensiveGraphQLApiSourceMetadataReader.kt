package funcify.feature.datasource.graphql.metadata

import arrow.core.identity
import funcify.feature.datasource.graphql.error.GQLDataSourceErrorResponse
import funcify.feature.datasource.graphql.error.GQLDataSourceException
import funcify.feature.datasource.graphql.schema.GraphQLInputFieldsContainerTypeExtractor
import funcify.feature.datasource.graphql.schema.GraphQLOutputFieldsContainerTypeExtractor
import funcify.feature.datasource.graphql.schema.GraphQLSourceIndex
import funcify.feature.datasource.graphql.schema.GraphQLSourceIndexFactory
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.SourceMetamodel
import funcify.feature.tools.control.RelationshipSpliterators
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import graphql.schema.*
import java.util.stream.Stream
import java.util.stream.Stream.empty
import java.util.stream.StreamSupport
import org.slf4j.Logger

/**
 *
 * @author smccarron
 * @created 2022-07-05
 */
class ComprehensiveGraphQLApiSourceMetadataReader(
    private val graphQLSourceIndexFactory: GraphQLSourceIndexFactory,
    private val graphQLSourceIndexCreationContextFactory: GraphQLSourceIndexCreationContextFactory
) : GraphQLApiSourceMetadataReader {

    companion object {
        private val logger: Logger = loggerFor<ComprehensiveGraphQLApiSourceMetadataReader>()
    }

    override fun readSourceMetamodelFromMetadata(
        dataSourceKey: DataSource.Key<GraphQLSourceIndex>,
        input: GraphQLSchema,
    ): SourceMetamodel<GraphQLSourceIndex> {
        logger.debug(
            """read_source_container_types_from_metadata: 
                |[ input.query_type.name: ${input.queryType.name}, 
                | query_type.field_definitions.size: ${input.queryType.fieldDefinitions.size} ]
                |""".flattenIntoOneLine()
        )
        if (input.queryType.fieldDefinitions.isEmpty()) {
            val message =
                """graphql_schema input for metadata on graphql 
                |source does not have any query type 
                |field definitions""".flattenIntoOneLine()
            logger.error(
                """read_source_container_types_from_metadata: 
                |[ error: ${message} ]
                |""".flattenIntoOneLine()
            )
            throw GQLDataSourceException(GQLDataSourceErrorResponse.INVALID_INPUT, message)
        }
        val rootSourceIndexContext: GraphQLSourceIndexCreationContext<GraphQLObjectType> =
            graphQLSourceIndexCreationContextFactory
                .createRootSourceIndexCreationContextForQueryGraphQLObjectType(input.queryType)
        return traverseAcrossSchemaElementsBreadthFirstPairingParentAndChildElements(
                input.queryType
            )
            .reduce(
                rootSourceIndexContext,
                { ctx, (parent, child) ->
                    foldParentChildElementsIntoSourceIndexCreationContext(ctx, parent, child)
                },
                { sic1, _ -> sic1 }
            )
            .let { sourceIndexCreationContext: GraphQLSourceIndexCreationContext<*> ->
                createSourceMetaModelFromLastSourceIndexCreationContext(sourceIndexCreationContext)
            }
    }

    private fun <E : GraphQLSchemaElement> createSourceMetaModelFromLastSourceIndexCreationContext(
        graphQLSourceIndexCreationContext: GraphQLSourceIndexCreationContext<E>
    ): SourceMetamodel<GraphQLSourceIndex> {
        TODO("Not yet implemented")
    }

    private fun <
        E1 : GraphQLSchemaElement,
        E2 : GraphQLSchemaElement> foldParentChildElementsIntoSourceIndexCreationContext(
        graphQLSourceIndexCreationContext: GraphQLSourceIndexCreationContext<E1>,
        parent: GraphQLSchemaElement,
        child: GraphQLSchemaElement
    ): GraphQLSourceIndexCreationContext<E2> {
        TODO()
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
                                    graphQLInputFieldsContainer.fieldDefinitions.stream()
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
                                graphQLInputFieldsContainer.fieldDefinitions.stream()
                            }
                            .fold(::empty, ::identity)
                    }
                    is GraphQLAppliedDirective -> {
                        parent.arguments.stream()
                    }
                    is GraphQLArgument -> {
                        GraphQLInputFieldsContainerTypeExtractor.invoke(parent.type)
                            .map { graphQLInputFieldsContainer: GraphQLInputFieldsContainer ->
                                graphQLInputFieldsContainer.fieldDefinitions.stream()
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
                                    graphQLFieldsContainer.fieldDefinitions.stream()
                                }
                                .fold(::empty, ::identity)
                        )
                    }
                    is GraphQLObjectType -> {
                        Stream.concat(
                            parent.appliedDirectives.stream(),
                            parent.fieldDefinitions.stream()
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
}

package funcify.feature.datasource.graphql.metadata.reader

import arrow.core.Option
import arrow.core.none
import arrow.core.or
import arrow.core.some
import funcify.feature.datasource.graphql.error.GQLDataSourceErrorResponse
import funcify.feature.datasource.graphql.error.GQLDataSourceException
import funcify.feature.datasource.graphql.metadata.reader.GraphQLSourceIndexCreationContext.Builder
import funcify.feature.datasource.graphql.metadata.reader.GraphQLSourceIndexCreationContext.DirectiveArgumentSourceIndexCreationContext
import funcify.feature.datasource.graphql.metadata.reader.GraphQLSourceIndexCreationContext.DirectiveSourceIndexCreationContext
import funcify.feature.datasource.graphql.metadata.reader.GraphQLSourceIndexCreationContext.FieldArgumentParameterSourceIndexCreationContext
import funcify.feature.datasource.graphql.metadata.reader.GraphQLSourceIndexCreationContext.FieldDefinitionSourceIndexCreationContext
import funcify.feature.datasource.graphql.metadata.reader.GraphQLSourceIndexCreationContext.InputObjectFieldSourceIndexCreationContext
import funcify.feature.datasource.graphql.metadata.reader.GraphQLSourceIndexCreationContext.InputObjectTypeSourceIndexCreationContext
import funcify.feature.datasource.graphql.metadata.reader.GraphQLSourceIndexCreationContext.OutputObjectTypeSourceIndexCreationContext
import funcify.feature.datasource.graphql.schema.GraphQLParameterAttribute
import funcify.feature.datasource.graphql.schema.GraphQLParameterContainerType
import funcify.feature.datasource.graphql.schema.GraphQLSourceAttribute
import funcify.feature.datasource.graphql.schema.GraphQLSourceContainerType
import funcify.feature.datasource.graphql.schema.GraphQLSourceIndex
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import graphql.schema.GraphQLAppliedDirective
import graphql.schema.GraphQLAppliedDirectiveArgument
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLInputObjectField
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchemaElement
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import org.slf4j.Logger

internal object DefaultGraphQLSourceIndexCreationContextFactory : GraphQLSourceIndexCreationContextFactory {

    internal class DefaultBuilder<E : GraphQLSchemaElement>(
        private var schematicPathCreatedBySchemaElement:
            PersistentMap<GraphQLSchemaElement, SchematicPath> =
            persistentMapOf(),
        private var schemaElementsBySchematicPath:
            PersistentMap<SchematicPath, PersistentSet<GraphQLSchemaElement>> =
            persistentMapOf(),
        private var graphqlSourceContainerTypesBySchematicPath:
            PersistentMap<SchematicPath, GraphQLSourceContainerType> =
            persistentMapOf(),
        private var graphqlSourceAttributesBySchematicPath:
            PersistentMap<SchematicPath, GraphQLSourceAttribute> =
            persistentMapOf(),
        private var graphqlParameterContainerTypesBySchematicPath:
            PersistentMap<SchematicPath, GraphQLParameterContainerType> =
            persistentMapOf(),
        private var graphqlParameterAttributesBySchematicPath:
            PersistentMap<SchematicPath, GraphQLParameterAttribute> =
            persistentMapOf(),
        private var graphQLApiDataSourceKey: DataSource.Key<GraphQLSourceIndex>,
        private var parentPath: Option<SchematicPath>,
        private var currentElement: E
    ) : Builder<E> {

        companion object {
            private val logger: Logger = loggerFor<DefaultBuilder<*>>()
        }

        override fun <SI : GraphQLSourceIndex> addOrUpdateGraphQLSourceIndex(
            graphQLSourceIndex: SI
        ): Builder<E> {
            logger.debug(
                """add_or_update_graphql_source_index: 
                   |[ graphql_source_index: { path: ${graphQLSourceIndex.sourcePath}, 
                   |name: ${graphQLSourceIndex.name} } ]
                   |""".flatten()
                        )
            when (graphQLSourceIndex) {
                is GraphQLSourceContainerType -> {
                    val containerTypePath = graphQLSourceIndex.sourcePath
                    graphqlSourceContainerTypesBySchematicPath =
                        graphqlSourceContainerTypesBySchematicPath.put(
                            containerTypePath,
                            graphQLSourceIndex
                        )
                    schematicPathCreatedBySchemaElement =
                        schematicPathCreatedBySchemaElement.put(
                            graphQLSourceIndex.graphQLFieldsContainerType,
                            containerTypePath
                        )
                    schemaElementsBySchematicPath =
                        schemaElementsBySchematicPath.put(
                            containerTypePath,
                            schemaElementsBySchematicPath
                                .getOrDefault(containerTypePath, persistentSetOf())
                                .add(graphQLSourceIndex.graphQLFieldsContainerType)
                        )
                }
                is GraphQLSourceAttribute -> {
                    val attributePath: SchematicPath = graphQLSourceIndex.sourcePath
                    graphqlSourceAttributesBySchematicPath =
                        graphqlSourceAttributesBySchematicPath.put(
                            attributePath,
                            graphQLSourceIndex
                        )
                    schematicPathCreatedBySchemaElement =
                        schematicPathCreatedBySchemaElement.put(
                            graphQLSourceIndex.graphQLFieldDefinition,
                            attributePath
                        )
                    schemaElementsBySchematicPath =
                        schemaElementsBySchematicPath.put(
                            attributePath,
                            schemaElementsBySchematicPath
                                .getOrDefault(attributePath, persistentSetOf())
                                .add(graphQLSourceIndex.graphQLFieldDefinition)
                        )
                }
                is GraphQLParameterContainerType -> {
                    val parameterContainerPath: SchematicPath = graphQLSourceIndex.sourcePath
                    graphqlParameterContainerTypesBySchematicPath =
                        graphqlParameterContainerTypesBySchematicPath.put(
                            parameterContainerPath,
                            graphQLSourceIndex
                        )

                    schematicPathCreatedBySchemaElement =
                        schematicPathCreatedBySchemaElement.put(
                            graphQLSourceIndex.inputFieldsContainerType,
                            parameterContainerPath
                        )
                    schemaElementsBySchematicPath =
                        schemaElementsBySchematicPath.put(
                            parameterContainerPath,
                            schemaElementsBySchematicPath
                                .getOrDefault(parameterContainerPath, persistentSetOf())
                                .add(graphQLSourceIndex.inputFieldsContainerType)
                        )
                }
                is GraphQLParameterAttribute -> {
                    val parameterAttributePath: SchematicPath = graphQLSourceIndex.sourcePath
                    graphqlParameterAttributesBySchematicPath =
                        graphqlParameterAttributesBySchematicPath.put(
                            parameterAttributePath,
                            graphQLSourceIndex
                        )
                    when (
                        val schemaElement: GraphQLSchemaElement? =
                            graphQLSourceIndex.fieldArgument
                                .or(graphQLSourceIndex.directiveArgument)
                                .or(graphQLSourceIndex.inputObjectField)
                                .or(graphQLSourceIndex.directive)
                                .orNull()
                    ) {
                        null -> {}
                        else -> {
                            schematicPathCreatedBySchemaElement =
                                schematicPathCreatedBySchemaElement.put(
                                    schemaElement,
                                    parameterAttributePath
                                )
                            schemaElementsBySchematicPath =
                                schemaElementsBySchematicPath.put(
                                    parameterAttributePath,
                                    schemaElementsBySchematicPath
                                        .getOrDefault(parameterAttributePath, persistentSetOf())
                                        .add(schemaElement)
                                )
                        }
                    }
                }
                else -> {
                    throw GQLDataSourceException(
                        GQLDataSourceErrorResponse.UNEXPECTED_ERROR,
                        """unhandled graphql_source_index type: 
                           |[ graphql_source_index.type: 
                           |${graphQLSourceIndex::class.qualifiedName} ]
                           |""".flatten()
                    )
                }
            }
            return this
        }

        override fun <NE : GraphQLSchemaElement> nextSchemaElement(
            parentPath: SchematicPath,
            nextElement: NE
        ): Builder<NE> {
            logger.debug(
                """next_schema_element: [ parent_path: ${parentPath}, 
                  |next_element.type: ${nextElement::class.simpleName} 
                  |]""".flatten()
                        )
            return DefaultBuilder(
                schematicPathCreatedBySchemaElement = schematicPathCreatedBySchemaElement,
                schemaElementsBySchematicPath = schemaElementsBySchematicPath,
                graphqlSourceContainerTypesBySchematicPath =
                    graphqlSourceContainerTypesBySchematicPath,
                graphqlSourceAttributesBySchematicPath = graphqlSourceAttributesBySchematicPath,
                graphqlParameterContainerTypesBySchematicPath =
                    graphqlParameterContainerTypesBySchematicPath,
                graphqlParameterAttributesBySchematicPath =
                    graphqlParameterAttributesBySchematicPath,
                graphQLApiDataSourceKey = graphQLApiDataSourceKey,
                parentPath = parentPath.some(),
                currentElement = nextElement
            )
        }

        override fun build(): GraphQLSourceIndexCreationContext<E> {
            @Suppress("UNCHECKED_CAST") //
            return when (val nextElement = currentElement) {
                is GraphQLObjectType -> {
                    DefaultOutputObjectTypeSourceIndexCreationContext(
                        schematicPathCreatedBySchemaElement = schematicPathCreatedBySchemaElement,
                        schemaElementsBySchematicPath = schemaElementsBySchematicPath,
                        graphqlSourceContainerTypesBySchematicPath =
                            graphqlSourceContainerTypesBySchematicPath,
                        graphqlSourceAttributesBySchematicPath =
                            graphqlSourceAttributesBySchematicPath,
                        graphqlParameterContainerTypesBySchematicPath =
                            graphqlParameterContainerTypesBySchematicPath,
                        graphqlParameterAttributesBySchematicPath =
                            graphqlParameterAttributesBySchematicPath,
                        graphQLApiDataSourceKey = graphQLApiDataSourceKey,
                        parentPath = parentPath,
                        currentElement = nextElement
                    )
                }
                is GraphQLFieldDefinition -> {
                    DefaultFieldDefinitionSourceIndexCreationContext(
                        schematicPathCreatedBySchemaElement = schematicPathCreatedBySchemaElement,
                        schemaElementsBySchematicPath = schemaElementsBySchematicPath,
                        graphqlSourceContainerTypesBySchematicPath =
                            graphqlSourceContainerTypesBySchematicPath,
                        graphqlSourceAttributesBySchematicPath =
                            graphqlSourceAttributesBySchematicPath,
                        graphqlParameterContainerTypesBySchematicPath =
                            graphqlParameterContainerTypesBySchematicPath,
                        graphqlParameterAttributesBySchematicPath =
                            graphqlParameterAttributesBySchematicPath,
                        graphQLApiDataSourceKey = graphQLApiDataSourceKey,
                        parentPath = parentPath,
                        currentElement = nextElement
                    )
                }
                is GraphQLArgument -> {
                    DefaultFieldArgumentParameterSourceIndexCreationContext(
                        schematicPathCreatedBySchemaElement = schematicPathCreatedBySchemaElement,
                        schemaElementsBySchematicPath = schemaElementsBySchematicPath,
                        graphqlSourceContainerTypesBySchematicPath =
                            graphqlSourceContainerTypesBySchematicPath,
                        graphqlSourceAttributesBySchematicPath =
                            graphqlSourceAttributesBySchematicPath,
                        graphqlParameterContainerTypesBySchematicPath =
                            graphqlParameterContainerTypesBySchematicPath,
                        graphqlParameterAttributesBySchematicPath =
                            graphqlParameterAttributesBySchematicPath,
                        graphQLApiDataSourceKey = graphQLApiDataSourceKey,
                        parentPath = parentPath,
                        currentElement = nextElement
                    )
                }
                is GraphQLInputObjectField -> {
                    DefaultInputObjectFieldSourceIndexCreationContext(
                        schematicPathCreatedBySchemaElement = schematicPathCreatedBySchemaElement,
                        schemaElementsBySchematicPath = schemaElementsBySchematicPath,
                        graphqlSourceContainerTypesBySchematicPath =
                            graphqlSourceContainerTypesBySchematicPath,
                        graphqlSourceAttributesBySchematicPath =
                            graphqlSourceAttributesBySchematicPath,
                        graphqlParameterContainerTypesBySchematicPath =
                            graphqlParameterContainerTypesBySchematicPath,
                        graphqlParameterAttributesBySchematicPath =
                            graphqlParameterAttributesBySchematicPath,
                        graphQLApiDataSourceKey = graphQLApiDataSourceKey,
                        parentPath = parentPath,
                        currentElement = nextElement
                    )
                }
                is GraphQLAppliedDirective -> {
                    DefaultDirectiveSourceIndexCreationContext(
                        schematicPathCreatedBySchemaElement = schematicPathCreatedBySchemaElement,
                        schemaElementsBySchematicPath = schemaElementsBySchematicPath,
                        graphqlSourceContainerTypesBySchematicPath =
                            graphqlSourceContainerTypesBySchematicPath,
                        graphqlSourceAttributesBySchematicPath =
                            graphqlSourceAttributesBySchematicPath,
                        graphqlParameterContainerTypesBySchematicPath =
                            graphqlParameterContainerTypesBySchematicPath,
                        graphqlParameterAttributesBySchematicPath =
                            graphqlParameterAttributesBySchematicPath,
                        graphQLApiDataSourceKey = graphQLApiDataSourceKey,
                        parentPath = parentPath,
                        currentElement = nextElement
                    )
                }
                is GraphQLAppliedDirectiveArgument -> {
                    DefaultDirectiveArgumentSourceIndexCreationContext(
                        schematicPathCreatedBySchemaElement = schematicPathCreatedBySchemaElement,
                        schemaElementsBySchematicPath = schemaElementsBySchematicPath,
                        graphqlSourceContainerTypesBySchematicPath =
                            graphqlSourceContainerTypesBySchematicPath,
                        graphqlSourceAttributesBySchematicPath =
                            graphqlSourceAttributesBySchematicPath,
                        graphqlParameterContainerTypesBySchematicPath =
                            graphqlParameterContainerTypesBySchematicPath,
                        graphqlParameterAttributesBySchematicPath =
                            graphqlParameterAttributesBySchematicPath,
                        graphQLApiDataSourceKey = graphQLApiDataSourceKey,
                        parentPath = parentPath,
                        currentElement = nextElement
                    )
                }
                is GraphQLInputObjectType -> {
                    DefaultInputObjectTypeSourceIndexCreationContext(
                        schematicPathCreatedBySchemaElement = schematicPathCreatedBySchemaElement,
                        schemaElementsBySchematicPath = schemaElementsBySchematicPath,
                        graphqlSourceContainerTypesBySchematicPath =
                            graphqlSourceContainerTypesBySchematicPath,
                        graphqlSourceAttributesBySchematicPath =
                            graphqlSourceAttributesBySchematicPath,
                        graphqlParameterContainerTypesBySchematicPath =
                            graphqlParameterContainerTypesBySchematicPath,
                        graphqlParameterAttributesBySchematicPath =
                            graphqlParameterAttributesBySchematicPath,
                        graphQLApiDataSourceKey = graphQLApiDataSourceKey,
                        parentPath = parentPath,
                        currentElement = nextElement,
                    )
                }
                else -> {
                    throw GQLDataSourceException(
                        GQLDataSourceErrorResponse.UNEXPECTED_ERROR,
                        """unhandled graphql_schema_element type: 
                            |[ actual: ${nextElement::class.qualifiedName} 
                            |]; check whether this schema_element type 
                            |requires an accompany source_index_type 
                            |before adding""".flatten()
                    )
                }
            }
                as GraphQLSourceIndexCreationContext<E>
        }
    }

    internal class DefaultOutputObjectTypeSourceIndexCreationContext(
        override val schematicPathCreatedBySchemaElement:
            PersistentMap<GraphQLSchemaElement, SchematicPath> =
            persistentMapOf(),
        override val schemaElementsBySchematicPath:
            PersistentMap<SchematicPath, PersistentSet<GraphQLSchemaElement>> =
            persistentMapOf(),
        override val graphqlSourceContainerTypesBySchematicPath:
            PersistentMap<SchematicPath, GraphQLSourceContainerType> =
            persistentMapOf(),
        override val graphqlSourceAttributesBySchematicPath:
            PersistentMap<SchematicPath, GraphQLSourceAttribute> =
            persistentMapOf(),
        override val graphqlParameterContainerTypesBySchematicPath:
            PersistentMap<SchematicPath, GraphQLParameterContainerType> =
            persistentMapOf(),
        override val graphqlParameterAttributesBySchematicPath:
            PersistentMap<SchematicPath, GraphQLParameterAttribute> =
            persistentMapOf(),
        override val graphQLApiDataSourceKey: DataSource.Key<GraphQLSourceIndex>,
        override val parentPath: Option<SchematicPath>,
        override val currentElement: GraphQLObjectType
    ) : OutputObjectTypeSourceIndexCreationContext {

        override fun <NE : GraphQLSchemaElement> update(
            transformer: (Builder<GraphQLObjectType>) -> Builder<NE>
        ): GraphQLSourceIndexCreationContext<NE> {
            val newBuilder =
                DefaultBuilder(
                    schematicPathCreatedBySchemaElement = schematicPathCreatedBySchemaElement,
                    schemaElementsBySchematicPath = schemaElementsBySchematicPath,
                    graphqlSourceContainerTypesBySchematicPath =
                        graphqlSourceContainerTypesBySchematicPath,
                    graphqlSourceAttributesBySchematicPath = graphqlSourceAttributesBySchematicPath,
                    graphqlParameterContainerTypesBySchematicPath =
                        graphqlParameterContainerTypesBySchematicPath,
                    graphqlParameterAttributesBySchematicPath =
                        graphqlParameterAttributesBySchematicPath,
                    graphQLApiDataSourceKey = graphQLApiDataSourceKey,
                    parentPath = parentPath,
                    currentElement = currentElement
                )
            return transformer.invoke(newBuilder).build()
        }
    }

    internal class DefaultFieldDefinitionSourceIndexCreationContext(
        override val schematicPathCreatedBySchemaElement:
            PersistentMap<GraphQLSchemaElement, SchematicPath> =
            persistentMapOf(),
        override val schemaElementsBySchematicPath:
            PersistentMap<SchematicPath, PersistentSet<GraphQLSchemaElement>> =
            persistentMapOf(),
        override val graphqlSourceContainerTypesBySchematicPath:
            PersistentMap<SchematicPath, GraphQLSourceContainerType> =
            persistentMapOf(),
        override val graphqlSourceAttributesBySchematicPath:
            PersistentMap<SchematicPath, GraphQLSourceAttribute> =
            persistentMapOf(),
        override val graphqlParameterContainerTypesBySchematicPath:
            PersistentMap<SchematicPath, GraphQLParameterContainerType> =
            persistentMapOf(),
        override val graphqlParameterAttributesBySchematicPath:
            PersistentMap<SchematicPath, GraphQLParameterAttribute> =
            persistentMapOf(),
        override val graphQLApiDataSourceKey: DataSource.Key<GraphQLSourceIndex>,
        override val parentPath: Option<SchematicPath>,
        override val currentElement: GraphQLFieldDefinition
    ) : FieldDefinitionSourceIndexCreationContext {

        override fun <NE : GraphQLSchemaElement> update(
            transformer: (Builder<GraphQLFieldDefinition>) -> Builder<NE>
        ): GraphQLSourceIndexCreationContext<NE> {
            val newBuilder =
                DefaultBuilder(
                    schematicPathCreatedBySchemaElement = schematicPathCreatedBySchemaElement,
                    schemaElementsBySchematicPath = schemaElementsBySchematicPath,
                    graphqlSourceContainerTypesBySchematicPath =
                        graphqlSourceContainerTypesBySchematicPath,
                    graphqlSourceAttributesBySchematicPath = graphqlSourceAttributesBySchematicPath,
                    graphqlParameterContainerTypesBySchematicPath =
                        graphqlParameterContainerTypesBySchematicPath,
                    graphqlParameterAttributesBySchematicPath =
                        graphqlParameterAttributesBySchematicPath,
                    graphQLApiDataSourceKey = graphQLApiDataSourceKey,
                    parentPath = parentPath,
                    currentElement = currentElement
                )
            return transformer.invoke(newBuilder).build()
        }
    }

    internal class DefaultFieldArgumentParameterSourceIndexCreationContext(
        override val schematicPathCreatedBySchemaElement:
            PersistentMap<GraphQLSchemaElement, SchematicPath> =
            persistentMapOf(),
        override val schemaElementsBySchematicPath:
            PersistentMap<SchematicPath, PersistentSet<GraphQLSchemaElement>> =
            persistentMapOf(),
        override val graphqlSourceContainerTypesBySchematicPath:
            PersistentMap<SchematicPath, GraphQLSourceContainerType> =
            persistentMapOf(),
        override val graphqlSourceAttributesBySchematicPath:
            PersistentMap<SchematicPath, GraphQLSourceAttribute> =
            persistentMapOf(),
        override val graphqlParameterContainerTypesBySchematicPath:
            PersistentMap<SchematicPath, GraphQLParameterContainerType> =
            persistentMapOf(),
        override val graphqlParameterAttributesBySchematicPath:
            PersistentMap<SchematicPath, GraphQLParameterAttribute> =
            persistentMapOf(),
        override val graphQLApiDataSourceKey: DataSource.Key<GraphQLSourceIndex>,
        override val parentPath: Option<SchematicPath>,
        override val currentElement: GraphQLArgument
    ) : FieldArgumentParameterSourceIndexCreationContext {

        override fun <NE : GraphQLSchemaElement> update(
            transformer: (Builder<GraphQLArgument>) -> Builder<NE>
        ): GraphQLSourceIndexCreationContext<NE> {
            val newBuilder =
                DefaultBuilder(
                    schematicPathCreatedBySchemaElement = schematicPathCreatedBySchemaElement,
                    schemaElementsBySchematicPath = schemaElementsBySchematicPath,
                    graphqlSourceContainerTypesBySchematicPath =
                        graphqlSourceContainerTypesBySchematicPath,
                    graphqlSourceAttributesBySchematicPath = graphqlSourceAttributesBySchematicPath,
                    graphqlParameterContainerTypesBySchematicPath =
                        graphqlParameterContainerTypesBySchematicPath,
                    graphqlParameterAttributesBySchematicPath =
                        graphqlParameterAttributesBySchematicPath,
                    graphQLApiDataSourceKey = graphQLApiDataSourceKey,
                    parentPath = parentPath,
                    currentElement = currentElement
                )
            return transformer.invoke(newBuilder).build()
        }
    }

    internal class DefaultInputObjectTypeSourceIndexCreationContext(
        override val schematicPathCreatedBySchemaElement:
            PersistentMap<GraphQLSchemaElement, SchematicPath> =
            persistentMapOf(),
        override val schemaElementsBySchematicPath:
            PersistentMap<SchematicPath, PersistentSet<GraphQLSchemaElement>> =
            persistentMapOf(),
        override val graphqlSourceContainerTypesBySchematicPath:
            PersistentMap<SchematicPath, GraphQLSourceContainerType> =
            persistentMapOf(),
        override val graphqlSourceAttributesBySchematicPath:
            PersistentMap<SchematicPath, GraphQLSourceAttribute> =
            persistentMapOf(),
        override val graphqlParameterContainerTypesBySchematicPath:
            PersistentMap<SchematicPath, GraphQLParameterContainerType> =
            persistentMapOf(),
        override val graphqlParameterAttributesBySchematicPath:
            PersistentMap<SchematicPath, GraphQLParameterAttribute> =
            persistentMapOf(),
        override val graphQLApiDataSourceKey: DataSource.Key<GraphQLSourceIndex>,
        override val parentPath: Option<SchematicPath>,
        override val currentElement: GraphQLInputObjectType
    ) : InputObjectTypeSourceIndexCreationContext {

        override fun <NE : GraphQLSchemaElement> update(
            transformer: (Builder<GraphQLInputObjectType>) -> Builder<NE>
        ): GraphQLSourceIndexCreationContext<NE> {
            val newBuilder =
                DefaultBuilder(
                    schematicPathCreatedBySchemaElement = schematicPathCreatedBySchemaElement,
                    schemaElementsBySchematicPath = schemaElementsBySchematicPath,
                    graphqlSourceContainerTypesBySchematicPath =
                        graphqlSourceContainerTypesBySchematicPath,
                    graphqlSourceAttributesBySchematicPath = graphqlSourceAttributesBySchematicPath,
                    graphqlParameterContainerTypesBySchematicPath =
                        graphqlParameterContainerTypesBySchematicPath,
                    graphqlParameterAttributesBySchematicPath =
                        graphqlParameterAttributesBySchematicPath,
                    graphQLApiDataSourceKey = graphQLApiDataSourceKey,
                    parentPath = parentPath,
                    currentElement = currentElement
                )
            return transformer.invoke(newBuilder).build()
        }
    }

    internal class DefaultInputObjectFieldSourceIndexCreationContext(
        override val schematicPathCreatedBySchemaElement:
            PersistentMap<GraphQLSchemaElement, SchematicPath> =
            persistentMapOf(),
        override val schemaElementsBySchematicPath:
            PersistentMap<SchematicPath, PersistentSet<GraphQLSchemaElement>> =
            persistentMapOf(),
        override val graphqlSourceContainerTypesBySchematicPath:
            PersistentMap<SchematicPath, GraphQLSourceContainerType> =
            persistentMapOf(),
        override val graphqlSourceAttributesBySchematicPath:
            PersistentMap<SchematicPath, GraphQLSourceAttribute> =
            persistentMapOf(),
        override val graphqlParameterContainerTypesBySchematicPath:
            PersistentMap<SchematicPath, GraphQLParameterContainerType> =
            persistentMapOf(),
        override val graphqlParameterAttributesBySchematicPath:
            PersistentMap<SchematicPath, GraphQLParameterAttribute> =
            persistentMapOf(),
        override val graphQLApiDataSourceKey: DataSource.Key<GraphQLSourceIndex>,
        override val parentPath: Option<SchematicPath>,
        override val currentElement: GraphQLInputObjectField
    ) : InputObjectFieldSourceIndexCreationContext {

        override fun <NE : GraphQLSchemaElement> update(
            transformer: (Builder<GraphQLInputObjectField>) -> Builder<NE>
        ): GraphQLSourceIndexCreationContext<NE> {
            val newBuilder =
                DefaultBuilder(
                    schematicPathCreatedBySchemaElement = schematicPathCreatedBySchemaElement,
                    schemaElementsBySchematicPath = schemaElementsBySchematicPath,
                    graphqlSourceContainerTypesBySchematicPath =
                        graphqlSourceContainerTypesBySchematicPath,
                    graphqlSourceAttributesBySchematicPath = graphqlSourceAttributesBySchematicPath,
                    graphqlParameterContainerTypesBySchematicPath =
                        graphqlParameterContainerTypesBySchematicPath,
                    graphqlParameterAttributesBySchematicPath =
                        graphqlParameterAttributesBySchematicPath,
                    graphQLApiDataSourceKey = graphQLApiDataSourceKey,
                    parentPath = parentPath,
                    currentElement = currentElement
                )
            return transformer.invoke(newBuilder).build()
        }
    }

    internal class DefaultDirectiveSourceIndexCreationContext(
        override val schematicPathCreatedBySchemaElement:
            PersistentMap<GraphQLSchemaElement, SchematicPath> =
            persistentMapOf(),
        override val schemaElementsBySchematicPath:
            PersistentMap<SchematicPath, PersistentSet<GraphQLSchemaElement>> =
            persistentMapOf(),
        override val graphqlSourceContainerTypesBySchematicPath:
            PersistentMap<SchematicPath, GraphQLSourceContainerType> =
            persistentMapOf(),
        override val graphqlSourceAttributesBySchematicPath:
            PersistentMap<SchematicPath, GraphQLSourceAttribute> =
            persistentMapOf(),
        override val graphqlParameterContainerTypesBySchematicPath:
            PersistentMap<SchematicPath, GraphQLParameterContainerType> =
            persistentMapOf(),
        override val graphqlParameterAttributesBySchematicPath:
            PersistentMap<SchematicPath, GraphQLParameterAttribute> =
            persistentMapOf(),
        override val graphQLApiDataSourceKey: DataSource.Key<GraphQLSourceIndex>,
        override val parentPath: Option<SchematicPath>,
        override val currentElement: GraphQLAppliedDirective
    ) : DirectiveSourceIndexCreationContext {

        override fun <NE : GraphQLSchemaElement> update(
            transformer: (Builder<GraphQLAppliedDirective>) -> Builder<NE>
        ): GraphQLSourceIndexCreationContext<NE> {
            val newBuilder =
                DefaultBuilder(
                    schematicPathCreatedBySchemaElement = schematicPathCreatedBySchemaElement,
                    schemaElementsBySchematicPath = schemaElementsBySchematicPath,
                    graphqlSourceContainerTypesBySchematicPath =
                        graphqlSourceContainerTypesBySchematicPath,
                    graphqlSourceAttributesBySchematicPath = graphqlSourceAttributesBySchematicPath,
                    graphqlParameterContainerTypesBySchematicPath =
                        graphqlParameterContainerTypesBySchematicPath,
                    graphqlParameterAttributesBySchematicPath =
                        graphqlParameterAttributesBySchematicPath,
                    graphQLApiDataSourceKey = graphQLApiDataSourceKey,
                    parentPath = parentPath,
                    currentElement = currentElement
                )
            return transformer.invoke(newBuilder).build()
        }
    }

    internal class DefaultDirectiveArgumentSourceIndexCreationContext(
        override val schematicPathCreatedBySchemaElement:
            PersistentMap<GraphQLSchemaElement, SchematicPath> =
            persistentMapOf(),
        override val schemaElementsBySchematicPath:
            PersistentMap<SchematicPath, PersistentSet<GraphQLSchemaElement>> =
            persistentMapOf(),
        override val graphqlSourceContainerTypesBySchematicPath:
            PersistentMap<SchematicPath, GraphQLSourceContainerType> =
            persistentMapOf(),
        override val graphqlSourceAttributesBySchematicPath:
            PersistentMap<SchematicPath, GraphQLSourceAttribute> =
            persistentMapOf(),
        override val graphqlParameterContainerTypesBySchematicPath:
            PersistentMap<SchematicPath, GraphQLParameterContainerType> =
            persistentMapOf(),
        override val graphqlParameterAttributesBySchematicPath:
            PersistentMap<SchematicPath, GraphQLParameterAttribute> =
            persistentMapOf(),
        override val graphQLApiDataSourceKey: DataSource.Key<GraphQLSourceIndex>,
        override val parentPath: Option<SchematicPath>,
        override val currentElement: GraphQLAppliedDirectiveArgument
    ) : DirectiveArgumentSourceIndexCreationContext {

        override fun <NE : GraphQLSchemaElement> update(
            transformer: (Builder<GraphQLAppliedDirectiveArgument>) -> Builder<NE>
        ): GraphQLSourceIndexCreationContext<NE> {
            val newBuilder =
                DefaultBuilder(
                    schematicPathCreatedBySchemaElement = schematicPathCreatedBySchemaElement,
                    schemaElementsBySchematicPath = schemaElementsBySchematicPath,
                    graphqlSourceContainerTypesBySchematicPath =
                        graphqlSourceContainerTypesBySchematicPath,
                    graphqlSourceAttributesBySchematicPath = graphqlSourceAttributesBySchematicPath,
                    graphqlParameterContainerTypesBySchematicPath =
                        graphqlParameterContainerTypesBySchematicPath,
                    graphqlParameterAttributesBySchematicPath =
                        graphqlParameterAttributesBySchematicPath,
                    graphQLApiDataSourceKey = graphQLApiDataSourceKey,
                    parentPath = parentPath,
                    currentElement = currentElement
                )
            return transformer.invoke(newBuilder).build()
        }
    }

    private val logger: Logger = loggerFor<DefaultGraphQLSourceIndexCreationContextFactory>()

    override fun createRootSourceIndexCreationContextForQueryGraphQLObjectType(
        graphQLApiDataSourceKey: DataSource.Key<GraphQLSourceIndex>,
        graphQLObjectType: GraphQLObjectType
    ): GraphQLSourceIndexCreationContext<GraphQLObjectType> {
        logger.debug(
            """create_root_source_index_creation_context
               |_for_query_graphql_object_type: [ graphql_object_type: 
               |{ name: ${graphQLObjectType.name}, 
               |field_definitions.size: ${graphQLObjectType.fieldDefinitions.size} 
               |} ]""".flatten()
                    )
        return DefaultOutputObjectTypeSourceIndexCreationContext(
            graphQLApiDataSourceKey = graphQLApiDataSourceKey,
            parentPath = none(),
            currentElement = graphQLObjectType
        )
    }
}

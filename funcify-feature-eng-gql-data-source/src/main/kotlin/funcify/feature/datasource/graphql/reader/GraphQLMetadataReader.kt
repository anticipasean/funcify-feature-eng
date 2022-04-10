package funcify.feature.datasource.graphql.reader

import arrow.core.firstOrNone
import arrow.core.getOrElse
import arrow.core.or
import arrow.core.toOption
import funcify.feature.datasource.graphql.naming.GraphQLSourceNamingConventions
import funcify.feature.datasource.graphql.schema.GraphQLSourceAttribute
import funcify.feature.datasource.graphql.schema.GraphQLSourceContainerType
import funcify.feature.datasource.graphql.schema.GraphQLSourceIndex
import funcify.feature.datasource.graphql.schema.GraphQLSourceMetamodel
import funcify.feature.schema.SchematicPath
import funcify.feature.schema.datasource.SourceMetamodel
import funcify.feature.schema.datasource.reader.MetadataReader
import funcify.feature.schema.path.DefaultSchematicPath
import funcify.feature.tools.control.ParentChildPairRecursiveSpliterator
import funcify.feature.tools.extensions.PersistentMapExtensions.combineWithPersistentSetValueMap
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLList
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLOutputType
import graphql.schema.GraphQLSchema
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.stream.Stream
import java.util.stream.StreamSupport


/**
 *
 * @author smccarron
 * @created 4/8/22
 */
class GraphQLMetadataReader : MetadataReader<GraphQLSchema, GraphQLSourceIndex> {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(GraphQLMetadataReader::class.java)

        private data class GqlSourceContext(val graphQLFieldDefinitionToPath: PersistentMap<GraphQLFieldDefinition, SchematicPath> = persistentMapOf(),
                                            val indicesByPath: PersistentMap<SchematicPath, PersistentSet<GraphQLSourceIndex>> = persistentMapOf(),
                                            val sourceContainerTypeToAttributeTypes: PersistentMap<GraphQLSourceContainerType, PersistentSet<GraphQLSourceAttribute>> = persistentMapOf(),
                                            val rootAttributes: PersistentSet<GraphQLSourceAttribute> = persistentSetOf()) {

        }

    }

    override fun readSourceContainerTypesFromMetadata(input: GraphQLSchema): SourceMetamodel<GraphQLSourceIndex> {
        logger.info("read_source_container_types_from_metadata: [ input.query_type.name: ${input.queryType.name} ]")
        if (input.queryType.fieldDefinitions.isEmpty()) {
            val message = "graphql_schema input for metadata on graphql source does not have any query type field definitions"
            logger.error("read_source_container_types_from_metadata: [ error: ${message} ]")
            throw IllegalArgumentException(message)
        }
        return input.queryType.fieldDefinitions.parallelStream()
                .reduce(GqlSourceContext(),
                        { gsc: GqlSourceContext, fieldDef: GraphQLFieldDefinition ->
                            addFieldDefinitionToGqlContext(gsc,
                                                           fieldDef)
                        },
                        { gsc1, gsc2 ->
                            combineTopLevelGqlSourceContexts(gsc1,
                                                             gsc2)
                        })
                .let { context: GqlSourceContext -> extractSourceContainerTypeIterableFromFinalContext(context) }

    }

    private fun addFieldDefinitionToGqlContext(gqlSourceContext: GqlSourceContext,
                                               graphQLFieldDefinition: GraphQLFieldDefinition): GqlSourceContext {
        return Stream.of(graphQLFieldDefinition)
                .flatMap { gqlFieldDef: GraphQLFieldDefinition ->
                    val traversalFunction: (GraphQLFieldDefinition) -> Stream<GraphQLFieldDefinition> = { gqlf: GraphQLFieldDefinition ->
                        when (val fieldType: GraphQLOutputType = gqlf.type) {
                            is GraphQLObjectType -> {
                                fieldType.fieldDefinitions.stream()
                            }
                            is GraphQLList -> {
                                if (fieldType.wrappedType is GraphQLObjectType) {
                                    (fieldType.wrappedType as GraphQLObjectType).fieldDefinitions.stream()
                                } else {
                                    Stream.empty<GraphQLFieldDefinition>()
                                }
                            }
                            else -> {
                                Stream.empty<GraphQLFieldDefinition>()
                            }
                        }
                    }
                    StreamSupport.stream(ParentChildPairRecursiveSpliterator(rootValue = gqlFieldDef,
                                                                             traversalFunction = traversalFunction),
                                         false)
                }
                .reduce(gqlSourceContext,
                        { gqlSrcCtx: GqlSourceContext, parentChildFieldDefPair: Pair<GraphQLFieldDefinition, GraphQLFieldDefinition> ->
                            addFieldDefinitionPairToGqlContext(currentContext = gqlSrcCtx,
                                                               parentFieldDefinition = parentChildFieldDefPair.first,
                                                               childFieldDefinition = parentChildFieldDefPair.second)
                        },
                        { gql1, gql2 -> // since not parallel, take right leaf node
                            gql2
                        })
    }


    private fun addFieldDefinitionPairToGqlContext(currentContext: GqlSourceContext,
                                                   parentFieldDefinition: GraphQLFieldDefinition,
                                                   childFieldDefinition: GraphQLFieldDefinition): GqlSourceContext {
        return when {
            !currentContext.graphQLFieldDefinitionToPath.containsKey(parentFieldDefinition) && !currentContext.graphQLFieldDefinitionToPath.containsKey(childFieldDefinition) -> {
                val parentConvPathName = GraphQLSourceNamingConventions.getPathNamingConventionForGraphQLFieldDefinitions()
                        .deriveName(parentFieldDefinition)
                val childConvPathName = GraphQLSourceNamingConventions.getPathNamingConventionForGraphQLFieldDefinitions()
                        .deriveName(childFieldDefinition)
                val parentConvFieldName = GraphQLSourceNamingConventions.getFieldNamingConventionForGraphQLFieldDefinitions()
                        .deriveName(parentFieldDefinition)
                val childConvFieldName = GraphQLSourceNamingConventions.getFieldNamingConventionForGraphQLFieldDefinitions()
                        .deriveName(childFieldDefinition)
                val parentPath: SchematicPath = DefaultSchematicPath(pathSegments = persistentListOf(parentConvPathName.qualifiedForm))
                val childPath: SchematicPath = DefaultSchematicPath(pathSegments = persistentListOf(parentConvPathName.qualifiedForm,
                                                                                                    childConvPathName.qualifiedForm))
                val parentAsContainerType: GraphQLSourceContainerType = GraphQLSourceContainerType(canonicalPath = parentPath,
                                                                                                   name = parentConvFieldName,
                                                                                                   type = parentFieldDefinition.type)
                val parentAsAttributeType: GraphQLSourceAttribute = GraphQLSourceAttribute(canonicalPath = parentPath,
                                                                                           name = parentConvFieldName,
                                                                                           type = parentFieldDefinition.type)
                val childAsAttributeType: GraphQLSourceAttribute = GraphQLSourceAttribute(canonicalPath = childPath,
                                                                                          name = childConvFieldName,
                                                                                          type = childFieldDefinition.type)
                val updatedAttributeSet = currentContext.sourceContainerTypeToAttributeTypes.getOrDefault(parentAsContainerType,
                                                                                                          persistentSetOf())
                        .add(childAsAttributeType)
                val updatedContainerTypeMap = currentContext.sourceContainerTypeToAttributeTypes.put(parentAsContainerType,
                                                                                                     updatedAttributeSet)
                val updatedRootAttributes = currentContext.rootAttributes.add(parentAsAttributeType)
                val updatedPathMap = currentContext.graphQLFieldDefinitionToPath.put(parentFieldDefinition,
                                                                                     parentPath)
                        .put(childFieldDefinition,
                             childPath)
                val updatedIndicesByPath = currentContext.indicesByPath.put(parentPath,
                                                                            persistentSetOf(parentAsContainerType,
                                                                                            parentAsAttributeType))
                        .put(childPath,
                             persistentSetOf(childAsAttributeType))
                currentContext.copy(sourceContainerTypeToAttributeTypes = updatedContainerTypeMap,
                                    rootAttributes = updatedRootAttributes,
                                    graphQLFieldDefinitionToPath = updatedPathMap,
                                    indicesByPath = updatedIndicesByPath)
            }
            !currentContext.graphQLFieldDefinitionToPath.containsKey(childFieldDefinition) -> {
                val childConvPathName = GraphQLSourceNamingConventions.getPathNamingConventionForGraphQLFieldDefinitions()
                        .deriveName(childFieldDefinition)
                val childConvFieldName = GraphQLSourceNamingConventions.getFieldNamingConventionForGraphQLFieldDefinitions()
                        .deriveName(childFieldDefinition)
                val parentPath = currentContext.graphQLFieldDefinitionToPath[parentFieldDefinition]!!
                val childPath = DefaultSchematicPath(pathSegments = parentPath.pathSegments.toPersistentList()
                        .add(childConvPathName.qualifiedForm))
                val parentAsContainerType = currentContext.indicesByPath[parentPath].toOption()
                        .flatMap { set ->
                            set.filterIsInstance<GraphQLSourceContainerType>()
                                    .firstOrNone()
                                    .or(set.filterIsInstance<GraphQLSourceAttribute>()
                                                .firstOrNone()
                                                .map { gqlAttr ->
                                                    GraphQLSourceContainerType(canonicalPath = gqlAttr.canonicalPath,
                                                                               name = gqlAttr.name,
                                                                               type = gqlAttr.type)
                                                })
                        }
                        .getOrElse {
                            GraphQLSourceContainerType(canonicalPath = parentPath,
                                                       name = GraphQLSourceNamingConventions.getFieldNamingConventionForGraphQLFieldDefinitions()
                                                               .deriveName(parentFieldDefinition),
                                                       type = parentFieldDefinition.type)
                        }
                val childAsAttributeType = GraphQLSourceAttribute(canonicalPath = childPath,
                                                                  name = childConvFieldName,
                                                                  type = childFieldDefinition.type)
                val updatedAttributeSet = currentContext.sourceContainerTypeToAttributeTypes.getOrDefault(parentAsContainerType,
                                                                                                          persistentSetOf(childAsAttributeType))
                val updatedContainerTypeMap = currentContext.sourceContainerTypeToAttributeTypes.put(parentAsContainerType,
                                                                                                     updatedAttributeSet)
                val updatedPathMap = currentContext.graphQLFieldDefinitionToPath.put(childFieldDefinition,
                                                                                     childPath)
                val updatedIndicesByPath = currentContext.indicesByPath.put(parentPath,
                                                                            currentContext.indicesByPath.getOrDefault(parentPath,
                                                                                                                      persistentSetOf())
                                                                                    .add(parentAsContainerType))
                        .put(childPath,
                             currentContext.indicesByPath.getOrDefault(childPath,
                                                                       persistentSetOf())
                                     .add(childAsAttributeType))

                currentContext.copy(sourceContainerTypeToAttributeTypes = updatedContainerTypeMap,
                                    graphQLFieldDefinitionToPath = updatedPathMap,
                                    indicesByPath = updatedIndicesByPath)
            }
            else -> {
                currentContext
            }
        }
    }

    private fun combineTopLevelGqlSourceContexts(gqlSourceContext1: GqlSourceContext,
                                                 gqlSourceContext2: GqlSourceContext): GqlSourceContext {
        val updatedGraphQLFieldDefinitionToPath =
                gqlSourceContext1.graphQLFieldDefinitionToPath.putAll(gqlSourceContext2.graphQLFieldDefinitionToPath)
        val updatedRootAttributes = gqlSourceContext1.rootAttributes.addAll(gqlSourceContext2.rootAttributes)
        val updatedIndicesByPath = gqlSourceContext2.indicesByPath.combineWithPersistentSetValueMap(gqlSourceContext1.indicesByPath)
        val updatedSourceContainerTypeToAttributeTypes =
                gqlSourceContext2.sourceContainerTypeToAttributeTypes.combineWithPersistentSetValueMap(gqlSourceContext1.sourceContainerTypeToAttributeTypes)
        return GqlSourceContext(graphQLFieldDefinitionToPath = updatedGraphQLFieldDefinitionToPath,
                                indicesByPath = updatedIndicesByPath,
                                sourceContainerTypeToAttributeTypes = updatedSourceContainerTypeToAttributeTypes,
                                rootAttributes = updatedRootAttributes)
    }

    private fun extractSourceContainerTypeIterableFromFinalContext(context: GqlSourceContext): GraphQLSourceMetamodel {
        val sourceIndicesWithAttributesInContainerTypes = context.sourceContainerTypeToAttributeTypes.entries.parallelStream()
                .reduce(persistentMapOf<SchematicPath, PersistentSet<GraphQLSourceIndex>>(),
                        { pm, entry: Map.Entry<GraphQLSourceContainerType, PersistentSet<GraphQLSourceAttribute>> ->
                            pm.put(entry.key.canonicalPath,
                                   pm.getOrDefault(entry.key.canonicalPath,
                                                   persistentSetOf())
                                           .add(entry.key.copy(sourceAttributes = entry.value)))
                        },
                        { pm1, pm2 ->
                            pm2.combineWithPersistentSetValueMap(pm1)
                        })
        val sourceIndicesWithRootAttributes = context.rootAttributes.parallelStream()
                .reduce(sourceIndicesWithAttributesInContainerTypes,
                        { pm, graphQLSourceAttribute: GraphQLSourceAttribute ->
                            pm.put(graphQLSourceAttribute.canonicalPath,
                                   pm.getOrDefault(graphQLSourceAttribute.canonicalPath,
                                                   persistentSetOf())
                                           .add(graphQLSourceAttribute))
                        },
                        { pm1, pm2 ->
                            pm2.combineWithPersistentSetValueMap(pm1)
                        })
        return GraphQLSourceMetamodel(sourceIndicesByPath = sourceIndicesWithRootAttributes)
    }

}
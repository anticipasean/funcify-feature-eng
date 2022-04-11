package funcify.feature.datasource.graphql.reader

import arrow.core.getOrElse
import arrow.core.or
import arrow.core.toOption
import funcify.feature.datasource.graphql.schema.GraphQLSourceAttribute
import funcify.feature.datasource.graphql.schema.GraphQLSourceContainerType
import funcify.feature.datasource.graphql.schema.GraphQLSourceIndex
import funcify.feature.datasource.graphql.schema.GraphQLSourceIndexFactory
import funcify.feature.datasource.graphql.schema.GraphQLSourceMetamodel
import funcify.feature.schema.datasource.SourceMetamodel
import funcify.feature.schema.datasource.reader.MetadataReader
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.control.ParentChildPairRecursiveSpliterator
import funcify.feature.tools.extensions.PersistentMapExtensions.combineWithPersistentSetValueMap
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLList
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLOutputType
import graphql.schema.GraphQLSchema
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.stream.Stream
import java.util.stream.StreamSupport


/**
 *
 * @author smccarron
 * @created 4/8/22
 */
class GraphQLMetadataReader() : MetadataReader<GraphQLSchema, GraphQLSourceIndex> {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(GraphQLMetadataReader::class.java)

        private data class GqlSourceContext(val graphQLFieldDefinitionToPath: PersistentMap<GraphQLFieldDefinition, SchematicPath> = persistentMapOf(),
                                            val indicesByPath: PersistentMap<SchematicPath, PersistentSet<GraphQLSourceIndex>> = persistentMapOf(),
                                            val sourceContainerTypeToAttributeTypes: PersistentMap<GraphQLSourceContainerType, PersistentSet<GraphQLSourceAttribute>> = persistentMapOf(),
                                            val rootAttributes: PersistentSet<GraphQLSourceAttribute> = persistentSetOf()) {

        }

    }

    override fun readSourceMetamodelFromMetadata(input: GraphQLSchema): SourceMetamodel<GraphQLSourceIndex> {
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
                .let { context: GqlSourceContext ->
                    extractSourceContainerTypeIterableFromFinalContext(context)
                }

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
                        { _, gql2 -> // since not parallel, take right leaf node
                            gql2
                        })
    }


    private fun addFieldDefinitionPairToGqlContext(currentContext: GqlSourceContext,
                                                   parentFieldDefinition: GraphQLFieldDefinition,
                                                   childFieldDefinition: GraphQLFieldDefinition): GqlSourceContext {
        return when {
            /**
             * Both parent and child field definitions have yet to be assigned schematic paths
             */
            !currentContext.graphQLFieldDefinitionToPath.containsKey(parentFieldDefinition) && !currentContext.graphQLFieldDefinitionToPath.containsKey(childFieldDefinition) -> {
                val rootIndices = GraphQLSourceIndexFactory.createRootIndices()
                        .fromRootDefinition(parentFieldDefinition)
                val parentAsContainerType: GraphQLSourceContainerType = rootIndices.asSequence()
                        .filterIsInstance<GraphQLSourceContainerType>()
                        .firstOrNull()
                        .toOption()
                        .getOrElse { throw IllegalArgumentException("parent_field_definition not for container_type") }
                val parentAsAttributeType: GraphQLSourceAttribute = rootIndices.asSequence()
                        .filterIsInstance<GraphQLSourceAttribute>()
                        .firstOrNull()
                        .toOption()
                        .getOrElse { throw IllegalStateException("parent_field_definition should have attribute representation") }
                val childAsAttributeType: GraphQLSourceAttribute = GraphQLSourceIndexFactory.createSourceAttribute()
                        .withParentPathAndDefinition(parentAsContainerType.sourcePath,
                                                     parentFieldDefinition)
                        .forChildAttributeDefinition(childFieldDefinition)
                val updatedPathMap = currentContext.graphQLFieldDefinitionToPath.put(parentFieldDefinition,
                                                                                     parentAsContainerType.sourcePath)
                        .put(childFieldDefinition,
                             childAsAttributeType.sourcePath)
                val updatedAttributeSet = currentContext.sourceContainerTypeToAttributeTypes.getOrDefault(parentAsContainerType,
                                                                                                          persistentSetOf())
                        .add(childAsAttributeType)
                val updatedContainerTypeMap = currentContext.sourceContainerTypeToAttributeTypes.put(parentAsContainerType,
                                                                                                     updatedAttributeSet)
                val updatedRootAttributes = currentContext.rootAttributes.add(parentAsAttributeType)
                val updatedIndicesByPath = currentContext.indicesByPath.put(parentAsContainerType.sourcePath,
                                                                            persistentSetOf(parentAsContainerType,
                                                                                            parentAsAttributeType))
                        .put(childAsAttributeType.sourcePath,
                             persistentSetOf(childAsAttributeType))
                currentContext.copy(sourceContainerTypeToAttributeTypes = updatedContainerTypeMap,
                                    rootAttributes = updatedRootAttributes,
                                    graphQLFieldDefinitionToPath = updatedPathMap,
                                    indicesByPath = updatedIndicesByPath)
            }
            /**
             * Case 2: Only the child field definition has yet to be assigned a schematic path
             */
            !currentContext.graphQLFieldDefinitionToPath.containsKey(childFieldDefinition) -> {
                val parentPath = currentContext.graphQLFieldDefinitionToPath[parentFieldDefinition]!!
                val parentAsContainerType: GraphQLSourceContainerType = currentContext.indicesByPath[parentPath].toOption()
                        .getOrElse { persistentSetOf() }
                        .asSequence()
                        .filterIsInstance<GraphQLSourceContainerType>()
                        .firstOrNull()
                        .toOption()
                        .or(GraphQLSourceIndexFactory.createSourceContainerType()
                                    .forAttributePathAndDefinition(parentPath,
                                                                   parentFieldDefinition))
                        .getOrElse { throw IllegalStateException("parent_type not container type") }
                val childAsAttributeType = GraphQLSourceIndexFactory.createSourceAttribute()
                        .withParentPathAndDefinition(parentPath,
                                                     parentFieldDefinition)
                        .forChildAttributeDefinition(childFieldDefinition)
                val updatedAttributeSet = currentContext.sourceContainerTypeToAttributeTypes.getOrDefault(parentAsContainerType,
                                                                                                          persistentSetOf())
                        .add(childAsAttributeType)
                val updatedContainerTypeMap = currentContext.sourceContainerTypeToAttributeTypes.put(parentAsContainerType,
                                                                                                     updatedAttributeSet)
                val updatedPathMap = currentContext.graphQLFieldDefinitionToPath.put(childFieldDefinition,
                                                                                     childAsAttributeType.sourcePath)
                val updatedIndicesByPath = currentContext.indicesByPath.put(parentPath,
                                                                            currentContext.indicesByPath.getOrDefault(parentPath,
                                                                                                                      persistentSetOf())
                                                                                    .add(parentAsContainerType))
                        .put(childAsAttributeType.sourcePath,
                             currentContext.indicesByPath.getOrDefault(childAsAttributeType.sourcePath,
                                                                       persistentSetOf())
                                     .add(childAsAttributeType))

                currentContext.copy(sourceContainerTypeToAttributeTypes = updatedContainerTypeMap,
                                    graphQLFieldDefinitionToPath = updatedPathMap,
                                    indicesByPath = updatedIndicesByPath)
            }
            else -> {
                /**
                 * Case  3: Both the parent and child field definitions already have schematic path mappings
                 */
                val parentPath = currentContext.graphQLFieldDefinitionToPath[parentFieldDefinition]!!
                val childPath = currentContext.graphQLFieldDefinitionToPath[childFieldDefinition]!!
                val parentAsContainerType = currentContext.indicesByPath[parentPath].toOption()
                        .getOrElse { persistentSetOf() }
                        .asSequence()
                        .filterIsInstance<GraphQLSourceContainerType>()
                        .firstOrNull()
                        .toOption()
                        .or(GraphQLSourceIndexFactory.createSourceContainerType()
                                    .forAttributePathAndDefinition(parentPath,
                                                                   parentFieldDefinition))
                        .getOrElse { throw IllegalStateException("parent_type not container type") }
                val childAsAttributeType = currentContext.indicesByPath[childPath].toOption()
                        .getOrElse { persistentSetOf() }
                        .asSequence()
                        .filterIsInstance<GraphQLSourceAttribute>()
                        .firstOrNull()
                        .toOption()
                        .getOrElse {
                            GraphQLSourceIndexFactory.createSourceAttribute()
                                    .withParentPathAndDefinition(parentPath,
                                                                 parentFieldDefinition)
                                    .forChildAttributeDefinition(childFieldDefinition)
                        }
                val updatedAttributeSet = currentContext.sourceContainerTypeToAttributeTypes.getOrDefault(parentAsContainerType,
                                                                                                          persistentSetOf())
                        .add(childAsAttributeType)
                val updatedContainerTypeMap = currentContext.sourceContainerTypeToAttributeTypes.put(parentAsContainerType,
                                                                                                     updatedAttributeSet)
                val updatedIndicesByPath = currentContext.indicesByPath.put(parentPath,
                                                                            currentContext.indicesByPath.getOrDefault(parentPath,
                                                                                                                      persistentSetOf())
                                                                                    .add(parentAsContainerType))
                        .put(childPath,
                             currentContext.indicesByPath.getOrDefault(childPath,
                                                                       persistentSetOf())
                                     .add(childAsAttributeType))

                currentContext.copy(sourceContainerTypeToAttributeTypes = updatedContainerTypeMap,
                                    indicesByPath = updatedIndicesByPath)
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
                            pm.put(entry.key.sourcePath,
                                   pm.getOrDefault(entry.key.sourcePath,
                                                   persistentSetOf())
                                           .add(entry.key.copy(sourceAttributes = entry.value)))
                        },
                        { pm1, pm2 ->
                            pm2.combineWithPersistentSetValueMap(pm1)
                        })
        val sourceIndicesWithRootAttributes = context.rootAttributes.parallelStream()
                .reduce(sourceIndicesWithAttributesInContainerTypes,
                        { pm, graphQLSourceAttribute: GraphQLSourceAttribute ->
                            pm.put(graphQLSourceAttribute.sourcePath,
                                   pm.getOrDefault(graphQLSourceAttribute.sourcePath,
                                                   persistentSetOf())
                                           .add(graphQLSourceAttribute))
                        },
                        { pm1, pm2 ->
                            pm2.combineWithPersistentSetValueMap(pm1)
                        })
        return GraphQLSourceMetamodel(sourceIndicesByPath = sourceIndicesWithRootAttributes)
    }

}
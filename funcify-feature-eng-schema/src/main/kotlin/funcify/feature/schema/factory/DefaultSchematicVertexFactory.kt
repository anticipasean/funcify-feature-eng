package funcify.feature.schema.factory

import arrow.core.None
import arrow.core.Option
import arrow.core.Some
import arrow.core.none
import arrow.core.some
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.SourceAttribute
import funcify.feature.schema.datasource.SourceContainerType
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.schema.error.SchemaErrorResponse
import funcify.feature.schema.error.SchemaException
import funcify.feature.schema.graph.DefaultJunctionVertex
import funcify.feature.schema.graph.DefaultLeafVertex
import funcify.feature.schema.graph.DefaultRootVertex
import funcify.feature.schema.graph.JunctionVertex
import funcify.feature.schema.graph.LeafVertex
import funcify.feature.schema.graph.RootVertex
import funcify.feature.schema.index.DefaultCompositeAttribute
import funcify.feature.schema.index.DefaultCompositeContainerType
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import org.slf4j.Logger

internal class DefaultSchematicVertexFactory() : SchematicVertexFactory {

    companion object {

        private val logger: Logger = loggerFor<DefaultSchematicVertexFactory>()
        private data class DefaultSourceIndexSpec(val schematicPath: SchematicPath) :
            SchematicVertexFactory.SourceIndexSpec {
            override fun <SI : SourceIndex> forSourceAttribute(
                sourceAttribute: SourceAttribute
            ): SchematicVertexFactory.DataSourceSpec<SI> {
                logger.debug(
                    "for_source_attribute: [ source_attribute.conventional_name: ${sourceAttribute.name} ]"
                )
                return DefaultDataSourceSpec<SI>(
                    schematicPath = schematicPath,
                    mappedSourceIndexAttempt =
                        assessWhetherAttributeInstanceOfExpectedSourceIndexType<SI>(sourceAttribute)
                )
            }

            override fun <SI : SourceIndex, A : SourceAttribute> forSourceContainerType(
                sourceContainerType: SourceContainerType<A>
            ): SchematicVertexFactory.DataSourceSpec<SI> {
                logger.debug(
                    "for_source_container_type: [ source_attribute.conventional_name: ${sourceContainerType.name} ]"
                )
                return DefaultDataSourceSpec<SI>(
                    schematicPath = schematicPath,
                    mappedSourceIndexAttempt =
                        assessWhetherContainerTypeInstanceOfExpectedSourceIndexType<SI>(
                            sourceContainerType
                        )
                )
            }

            override fun fromExistingVertex(
                existingSchematicVertex: SchematicVertex
            ): SchematicVertexFactory.ExistingSchematicVertexSpec {
                logger.debug(
                    "from_existing_vertex: [ existing_schematic_vertex.type: ${existingSchematicVertex::class.qualifiedName} ]"
                )
                if (schematicPath != existingSchematicVertex.path) {
                    val message: String =
                        """schematic_path of existing vertex does 
                    |not match input schematic vertex: [ expected: "$schematicPath" 
                    |vs. actual: "${existingSchematicVertex.path}" ]
                    |""".flattenIntoOneLine()
                    logger.error("from_existing_vertex: [ status: failed ]: $message")
                    throw SchemaException(SchemaErrorResponse.INVALID_INPUT, message)
                }
                return DefaultExistingSchematicVertexSpec(
                    schematicPath = schematicPath,
                    existingSchematicVertex = existingSchematicVertex
                )
            }
        }

        private fun <SI : SourceIndex> assessWhetherContainerTypeInstanceOfExpectedSourceIndexType(
            sourceContainerType: SourceContainerType<*>
        ): Try<SI> {
            return Try.attemptNullable(
                {
                    @Suppress("UNCHECKED_CAST") //
                    sourceContainerType as? SI
                },
                {
                    SchemaException(
                        SchemaErrorResponse.INVALID_INPUT,
                        """source_container [ type: ${sourceContainerType::class.qualifiedName} ] 
                           |is not instance of expected data source index type
                           |""".flattenIntoOneLine()
                    )
                }
            )
        }

        private fun <SI : SourceIndex> assessWhetherAttributeInstanceOfExpectedSourceIndexType(
            sourceAttribute: SourceAttribute
        ): Try<SI> {
            return Try.attemptNullable(
                {
                    @Suppress("UNCHECKED_CAST") //
                    sourceAttribute as? SI
                },
                {
                    SchemaException(
                        SchemaErrorResponse.INVALID_INPUT,
                        """source_attribute [ type: ${sourceAttribute::class.qualifiedName} ] 
                           |is not instance of expected data source index type
                           |""".flattenIntoOneLine()
                    )
                }
            )
        }

        private data class DefaultExistingSchematicVertexSpec(
            val schematicPath: SchematicPath,
            val existingSchematicVertex: SchematicVertex
        ) : SchematicVertexFactory.ExistingSchematicVertexSpec {
            override fun <SI : SourceIndex> forSourceAttribute(
                sourceAttribute: SourceAttribute
            ): SchematicVertexFactory.DataSourceSpec<SI> {
                logger.debug(
                    "for_source_attribute: [ source_attribute.conventional_name: ${sourceAttribute.name} ]"
                )
                return DefaultDataSourceSpec<SI>(
                    schematicPath = schematicPath,
                    mappedSourceIndexAttempt =
                        assessWhetherAttributeInstanceOfExpectedSourceIndexType<SI>(
                            sourceAttribute
                        ),
                    existingSchematicVertexOpt = existingSchematicVertex.some()
                )
            }

            override fun <SI : SourceIndex, A : SourceAttribute> forSourceContainerType(
                sourceContainerType: SourceContainerType<A>
            ): SchematicVertexFactory.DataSourceSpec<SI> {
                logger.debug(
                    "for_source_container_type: [ source_attribute.conventional_name: ${sourceContainerType.name} ]"
                )
                return DefaultDataSourceSpec<SI>(
                    schematicPath = schematicPath,
                    mappedSourceIndexAttempt =
                        assessWhetherContainerTypeInstanceOfExpectedSourceIndexType<SI>(
                            sourceContainerType
                        ),
                    existingSchematicVertexOpt = existingSchematicVertex.some()
                )
            }
        }

        private data class DefaultDataSourceSpec<SI : SourceIndex>(
            val schematicPath: SchematicPath,
            val mappedSourceIndexAttempt: Try<SI>,
            val existingSchematicVertexOpt: Option<SchematicVertex> = none()
        ) : SchematicVertexFactory.DataSourceSpec<SI> {
            override fun onDataSource(dataSource: DataSource<SI>): SchematicVertex {
                logger.debug(
                    "on_data_source: [ data_source.source_type: ${dataSource.sourceType} ]"
                )
                if (mappedSourceIndexAttempt.isFailure()) {
                    mappedSourceIndexAttempt.ifFailed { throwable: Throwable ->
                        logger.error(
                            """on_data_source: [ error: [ type: ${throwable::class.qualifiedName}, 
                               |message: "${throwable.message}
                               |] ]""".flattenIntoOneLine()
                        )
                    }
                    throw mappedSourceIndexAttempt.getFailure().orNull()!!
                }
                return when (val sourceIndex: SI = mappedSourceIndexAttempt.orElseThrow()) {
                    is SourceAttribute -> {
                        when (existingSchematicVertexOpt) {
                            is Some -> {
                                when (val existingVertex = existingSchematicVertexOpt.value) {
                                    is LeafVertex ->
                                        DefaultLeafVertex(
                                            path = schematicPath,
                                            compositeAttribute =
                                                DefaultCompositeAttribute(
                                                    conventionalName =
                                                        existingVertex
                                                            .compositeAttribute
                                                            .conventionalName,
                                                    existingVertex
                                                        .compositeAttribute
                                                        .getSourceAttributeByDataSource()
                                                        .toPersistentMap()
                                                        .put(dataSource, sourceIndex)
                                                )
                                        )
                                    is JunctionVertex ->
                                        DefaultJunctionVertex(
                                            path = schematicPath,
                                            compositeAttribute =
                                                DefaultCompositeAttribute(
                                                    conventionalName =
                                                        existingVertex
                                                            .compositeAttribute
                                                            .conventionalName,
                                                    sourceAttributesByDataSource =
                                                        existingVertex
                                                            .compositeAttribute
                                                            .getSourceAttributeByDataSource()
                                                            .toPersistentMap()
                                                            .put(dataSource, sourceIndex)
                                                ),
                                            compositeContainerType =
                                                existingVertex.compositeContainerType
                                        )
                                    else -> {
                                        throw SchemaException(
                                            SchemaErrorResponse.UNEXPECTED_ERROR,
                                            "unhandled graph index type on existing vertex: ${existingVertex::class.qualifiedName}"
                                        )
                                    }
                                }
                            }
                            is None -> {
                                DefaultLeafVertex(
                                    path = schematicPath,
                                    compositeAttribute =
                                        DefaultCompositeAttribute(
                                            /**
                                             * Location where entity resolution and application of
                                             * naming conventions can be done in the future
                                             */
                                            conventionalName = sourceIndex.name,
                                            sourceAttributesByDataSource =
                                                persistentMapOf(dataSource to sourceIndex)
                                        )
                                )
                            }
                        }
                    }
                    is SourceContainerType<*> -> {
                        when (existingSchematicVertexOpt) {
                            is Some -> {
                                when (val existingVertex = existingSchematicVertexOpt.value) {
                                    is RootVertex ->
                                        DefaultRootVertex(
                                            path = schematicPath,
                                            compositeContainerType =
                                                DefaultCompositeContainerType(
                                                    conventionalName =
                                                        existingVertex
                                                            .compositeContainerType
                                                            .conventionalName,
                                                    sourceContainerTypesByDataSource =
                                                        existingVertex
                                                            .compositeContainerType
                                                            .getSourceContainerTypeByDataSource()
                                                            .toPersistentMap()
                                                            .put(dataSource, sourceIndex)
                                                )
                                        )
                                    is LeafVertex ->
                                        DefaultJunctionVertex(
                                            path = schematicPath,
                                            compositeContainerType =
                                                DefaultCompositeContainerType(
                                                    conventionalName =
                                                        existingVertex
                                                            .compositeAttribute
                                                            .conventionalName,
                                                    sourceContainerTypesByDataSource =
                                                        persistentMapOf(dataSource to sourceIndex)
                                                ),
                                            compositeAttribute = existingVertex.compositeAttribute
                                        )
                                    is JunctionVertex ->
                                        DefaultJunctionVertex(
                                            path = schematicPath,
                                            compositeContainerType =
                                                DefaultCompositeContainerType(
                                                    conventionalName =
                                                        existingVertex
                                                            .compositeContainerType
                                                            .conventionalName,
                                                    sourceContainerTypesByDataSource =
                                                        existingVertex
                                                            .compositeContainerType
                                                            .getSourceContainerTypeByDataSource()
                                                            .toPersistentMap()
                                                            .put(dataSource, sourceIndex)
                                                ),
                                            compositeAttribute = existingVertex.compositeAttribute
                                        )
                                    else -> {
                                        throw SchemaException(
                                            SchemaErrorResponse.UNEXPECTED_ERROR,
                                            "unhandled graph index type on existing vertex: ${existingVertex::class.qualifiedName}"
                                        )
                                    }
                                }
                            }
                            is None -> {
                                DefaultJunctionVertex(
                                    path = schematicPath,
                                    compositeContainerType =
                                        DefaultCompositeContainerType(
                                            /**
                                             * Another location where entity resolution and
                                             * application of naming conventions can be done in the
                                             * future
                                             */
                                            conventionalName = sourceIndex.name,
                                            sourceContainerTypesByDataSource =
                                                persistentMapOf(dataSource to sourceIndex)
                                        ),
                                    compositeAttribute =
                                        DefaultCompositeAttribute(
                                            /**
                                             * Another location where entity resolution and
                                             * application of naming conventions can be done in the
                                             * future
                                             */
                                            conventionalName = sourceIndex.name,
                                            sourceAttributesByDataSource = persistentMapOf()
                                        )
                                )
                            }
                        }
                    }
                    else -> {
                        throw SchemaException(
                            SchemaErrorResponse.UNEXPECTED_ERROR,
                            "unhandled source index type: ${sourceIndex::class.qualifiedName}"
                        )
                    }
                }
            }
        }
    }

    override fun createVertexForPath(
        schematicPath: SchematicPath
    ): SchematicVertexFactory.SourceIndexSpec {
        logger.debug("create_vertex_for_path: [ path: $schematicPath ]")
        return DefaultSourceIndexSpec(schematicPath = schematicPath)
    }
}

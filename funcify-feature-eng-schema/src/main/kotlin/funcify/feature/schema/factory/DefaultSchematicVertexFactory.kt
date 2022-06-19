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
import funcify.feature.schema.index.DefaultCompositeSourceAttribute
import funcify.feature.schema.index.DefaultCompositeSourceContainerType
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.vertex.DefaultSourceJunctionVertex
import funcify.feature.schema.vertex.DefaultSourceLeafVertex
import funcify.feature.schema.vertex.DefaultSourceRootVertex
import funcify.feature.schema.vertex.SourceJunctionVertex
import funcify.feature.schema.vertex.SourceLeafVertex
import funcify.feature.schema.vertex.SourceRootVertex
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import org.slf4j.Logger

internal class DefaultSchematicVertexFactory : SchematicVertexFactory {

    companion object {

        private val logger: Logger = loggerFor<DefaultSchematicVertexFactory>()
        private data class DefaultSourceIndexSpec(val schematicPath: SchematicPath) :
            SchematicVertexFactory.SourceIndexSpec {
            override fun <SI : SourceIndex<SI>> forSourceAttribute(
                sourceAttribute: SourceAttribute<SI>
            ): SchematicVertexFactory.DataSourceSpec<SI> {
                logger.debug(
                    """for_source_attribute: [ source_attribute.
                        |conventional_name: ${sourceAttribute.name} 
                        |]""".flattenIntoOneLine()
                )
                return DefaultDataSourceSpec<SI>(
                    schematicPath = schematicPath,
                    mappedSourceIndexAttempt =
                        assessWhetherAttributeInstanceOfExpectedSourceIndexType<SI>(sourceAttribute)
                )
            }

            override fun <SI : SourceIndex<SI>, A : SourceAttribute<SI>> forSourceContainerType(
                sourceContainerType: SourceContainerType<SI, A>
            ): SchematicVertexFactory.DataSourceSpec<SI> {
                logger.debug(
                    """for_source_container_type: [ source_container_type.
                        |conventional_name: ${sourceContainerType.name} ]
                        |""".flattenIntoOneLine()
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

        private fun <
            SI : SourceIndex<SI>> assessWhetherContainerTypeInstanceOfExpectedSourceIndexType(
            sourceContainerType: SourceContainerType<SI, *>
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

        private fun <SI : SourceIndex<SI>> assessWhetherAttributeInstanceOfExpectedSourceIndexType(
            sourceAttribute: SourceAttribute<SI>
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
            override fun <SI : SourceIndex<SI>> forSourceAttribute(
                sourceAttribute: SourceAttribute<SI>
            ): SchematicVertexFactory.DataSourceSpec<SI> {
                logger.debug(
                    """for_source_attribute: [ source_attribute.
                        |conventional_name: ${sourceAttribute.name} ]
                        |""".flattenIntoOneLine()
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

            override fun <SI : SourceIndex<SI>, A : SourceAttribute<SI>> forSourceContainerType(
                sourceContainerType: SourceContainerType<SI, A>
            ): SchematicVertexFactory.DataSourceSpec<SI> {
                logger.debug(
                    """for_source_container_type: 
                        |[ source_container_type.conventional_name: 
                        |${sourceContainerType.name} ]""".flattenIntoOneLine()
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

        private data class DefaultDataSourceSpec<SI : SourceIndex<SI>>(
            val schematicPath: SchematicPath,
            val mappedSourceIndexAttempt: Try<SI>,
            val existingSchematicVertexOpt: Option<SchematicVertex> = none()
        ) : SchematicVertexFactory.DataSourceSpec<SI> {
            override fun onDataSource(dataSource: DataSource<SI>): Try<SchematicVertex> {
                logger.debug(
                    """on_data_source: [ source_type: ${dataSource.dataSourceType}, 
                       |name: ${dataSource.name} ]""".flattenIntoOneLine()
                )
                if (mappedSourceIndexAttempt.isFailure()) {
                    mappedSourceIndexAttempt.ifFailed { throwable: Throwable ->
                        logger.error(
                            """|on_data_source: [ error: [ type: ${throwable::class.qualifiedName}, 
                               |message: "${throwable.message}
                               |] ]""".flattenIntoOneLine()
                        )
                    }
                    return Try.failure<SchematicVertex>(
                        mappedSourceIndexAttempt.getFailure().orNull()!!
                    )
                }
                return Try.attempt {
                    when (val sourceIndex: SI = mappedSourceIndexAttempt.orElseThrow()) {
                        is SourceAttribute<*> -> {
                            when (existingSchematicVertexOpt) {
                                is Some -> {
                                    when (val existingVertex = existingSchematicVertexOpt.value) {
                                        is SourceLeafVertex ->
                                            DefaultSourceLeafVertex(
                                                path = schematicPath,
                                                compositeAttribute =
                                                    DefaultCompositeSourceAttribute(
                                                        conventionalName =
                                                            existingVertex
                                                                .compositeAttribute
                                                                .conventionalName,
                                                        existingVertex
                                                            .compositeAttribute
                                                            .getSourceAttributeByDataSource()
                                                            .toPersistentMap()
                                                            .put(dataSource.key, sourceIndex)
                                                    )
                                            )
                                        is SourceJunctionVertex ->
                                            DefaultSourceJunctionVertex(
                                                path = schematicPath,
                                                compositeAttribute =
                                                    DefaultCompositeSourceAttribute(
                                                        conventionalName =
                                                            existingVertex
                                                                .compositeAttribute
                                                                .conventionalName,
                                                        sourceAttributesByDataSource =
                                                            existingVertex
                                                                .compositeAttribute
                                                                .getSourceAttributeByDataSource()
                                                                .toPersistentMap()
                                                                .put(dataSource.key, sourceIndex)
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
                                    DefaultSourceLeafVertex(
                                        path = schematicPath,
                                        compositeAttribute =
                                            DefaultCompositeSourceAttribute(
                                                /**
                                                 * Location where entity resolution and application
                                                 * of naming conventions can be done in the future
                                                 */
                                                conventionalName = sourceIndex.name,
                                                sourceAttributesByDataSource =
                                                    persistentMapOf(dataSource.key to sourceIndex)
                                            )
                                    )
                                }
                            }
                        }
                        is SourceContainerType<*, *> -> {
                            when (existingSchematicVertexOpt) {
                                is Some -> {
                                    when (val existingVertex = existingSchematicVertexOpt.value) {
                                        is SourceRootVertex ->
                                            DefaultSourceRootVertex(
                                                path = schematicPath,
                                                compositeContainerType =
                                                    DefaultCompositeSourceContainerType(
                                                        conventionalName =
                                                            existingVertex
                                                                .compositeContainerType
                                                                .conventionalName,
                                                        sourceContainerTypesByDataSource =
                                                            existingVertex
                                                                .compositeContainerType
                                                                .getSourceContainerTypeByDataSource()
                                                                .toPersistentMap()
                                                                .put(dataSource.key, sourceIndex)
                                                    )
                                            )
                                        is SourceLeafVertex ->
                                            DefaultSourceJunctionVertex(
                                                path = schematicPath,
                                                compositeContainerType =
                                                    DefaultCompositeSourceContainerType(
                                                        conventionalName =
                                                            existingVertex
                                                                .compositeAttribute
                                                                .conventionalName,
                                                        sourceContainerTypesByDataSource =
                                                            persistentMapOf(
                                                                dataSource.key to sourceIndex
                                                            )
                                                    ),
                                                compositeAttribute =
                                                    existingVertex.compositeAttribute
                                            )
                                        is SourceJunctionVertex ->
                                            DefaultSourceJunctionVertex(
                                                path = schematicPath,
                                                compositeContainerType =
                                                    DefaultCompositeSourceContainerType(
                                                        conventionalName =
                                                            existingVertex
                                                                .compositeContainerType
                                                                .conventionalName,
                                                        sourceContainerTypesByDataSource =
                                                            existingVertex
                                                                .compositeContainerType
                                                                .getSourceContainerTypeByDataSource()
                                                                .toPersistentMap()
                                                                .put(dataSource.key, sourceIndex)
                                                    ),
                                                compositeAttribute =
                                                    existingVertex.compositeAttribute
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
                                    if (schematicPath.isRoot()) {
                                        DefaultSourceRootVertex(
                                            path = schematicPath,
                                            compositeContainerType =
                                                DefaultCompositeSourceContainerType(
                                                    sourceIndex.name,
                                                    persistentMapOf(dataSource.key to sourceIndex)
                                                )
                                        )
                                    } else {
                                        DefaultSourceJunctionVertex(
                                            path = schematicPath,
                                            compositeContainerType =
                                                DefaultCompositeSourceContainerType(
                                                    /**
                                                     * Another location where entity resolution and
                                                     * application of naming conventions can be done
                                                     * in the future
                                                     */
                                                    conventionalName = sourceIndex.name,
                                                    sourceContainerTypesByDataSource =
                                                        persistentMapOf(
                                                            dataSource.key to sourceIndex
                                                        )
                                                ),
                                            compositeAttribute =
                                                DefaultCompositeSourceAttribute(
                                                    /**
                                                     * Another location where entity resolution and
                                                     * application of naming conventions can be done
                                                     * in the future
                                                     */
                                                    conventionalName = sourceIndex.name,
                                                    sourceAttributesByDataSource = persistentMapOf()
                                                )
                                        )
                                    }
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
    }

    override fun createVertexForPath(
        schematicPath: SchematicPath
    ): SchematicVertexFactory.SourceIndexSpec {
        logger.debug("create_vertex_for_path: [ path: $schematicPath ]")
        return DefaultSourceIndexSpec(schematicPath = schematicPath)
    }
}

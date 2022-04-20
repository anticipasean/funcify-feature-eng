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
import funcify.feature.schema.graph.JunctionVertex
import funcify.feature.schema.graph.LeafVertex
import funcify.feature.schema.index.DefaultCompositeAttribute
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
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
                return DefaultDataSourceSpec<SI>(
                    schematicPath = schematicPath,
                    mappedSourceIndexAttempt =
                        assessWhetherAttributeInstanceOfExpectedSourceIndexType<SI>(sourceAttribute)
                )
            }

            override fun <SI : SourceIndex, A : SourceAttribute> forSourceContainerType(
                sourceContainerType: SourceContainerType<A>
            ): SchematicVertexFactory.DataSourceSpec<SI> {
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
                logger.info("on_data_source: [ data_source.source_type: ${dataSource.sourceType} ]")
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
                                TODO("Not yet implemented")
                            }
                        }
                    }
                    is SourceContainerType<*> -> {
                        when (existingSchematicVertexOpt) {
                            is Some -> {
                                TODO("Not yet implemented")
                            }
                            is None -> {
                                TODO("Not yet implemented")
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
        return DefaultSourceIndexSpec(schematicPath = schematicPath)
    }
}

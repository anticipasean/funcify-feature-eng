package funcify.feature.schema.factory

import arrow.core.Option
import arrow.core.none
import arrow.core.some
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.ParameterAttribute
import funcify.feature.schema.datasource.ParameterContainerType
import funcify.feature.schema.datasource.SourceAttribute
import funcify.feature.schema.datasource.SourceContainerType
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.schema.error.SchemaErrorResponse
import funcify.feature.schema.error.SchemaException
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import funcify.feature.tools.extensions.TryExtensions.successIfNonNull
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
                        @Suppress("UNCHECKED_CAST") //
                        (sourceAttribute as? SI).successIfNonNull()
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
                        @Suppress("UNCHECKED_CAST") //
                        (sourceContainerType as? SI).successIfNonNull()
                )
            }

            override fun <SI : SourceIndex<SI>> forParameterAttribute(
                parameterAttribute: ParameterAttribute<SI>
            ): SchematicVertexFactory.DataSourceSpec<SI> {
                logger.debug(
                    """for_parameter_attribute: [ parameter_attribute.name: ${parameterAttribute.name} ]"""
                )
                return DefaultDataSourceSpec<SI>(
                    schematicPath = schematicPath,
                    mappedSourceIndexAttempt =
                        @Suppress("UNCHECKED_CAST") //
                        (parameterAttribute as? SI).successIfNonNull()
                )
            }

            override fun <
                SI : SourceIndex<SI>, A : ParameterAttribute<SI>> forParameterContainerType(
                parameterContainerType: ParameterContainerType<SI, A>
            ): SchematicVertexFactory.DataSourceSpec<SI> {
                logger.debug(
                    "for_parameter_container_type: [ parameter_container_type.name: ${parameterContainerType.name} ]"
                )
                return DefaultDataSourceSpec<SI>(
                    schematicPath = schematicPath,
                    mappedSourceIndexAttempt =
                        @Suppress("UNCHECKED_CAST") //
                        (parameterContainerType as? SI).successIfNonNull()
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
                        @Suppress("UNCHECKED_CAST") //
                        (sourceAttribute as? SI).successIfNonNull(),
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
                        @Suppress("UNCHECKED_CAST") //
                        (sourceContainerType as? SI).successIfNonNull(),
                    existingSchematicVertexOpt = existingSchematicVertex.some()
                )
            }

            override fun <SI : SourceIndex<SI>> forParameterAttribute(
                parameterAttribute: ParameterAttribute<SI>
            ): SchematicVertexFactory.DataSourceSpec<SI> {
                logger.debug(
                    """for_parameter_attribute: [ parameter_attribute.name: ${parameterAttribute.name} ]"""
                )
                return DefaultDataSourceSpec<SI>(
                    schematicPath = schematicPath,
                    mappedSourceIndexAttempt =
                        @Suppress("UNCHECKED_CAST") //
                        (parameterAttribute as? SI).successIfNonNull(),
                    existingSchematicVertexOpt = existingSchematicVertex.some()
                )
            }

            override fun <
                SI : SourceIndex<SI>, A : ParameterAttribute<SI>> forParameterContainerType(
                parameterContainerType: ParameterContainerType<SI, A>
            ): SchematicVertexFactory.DataSourceSpec<SI> {
                logger.debug(
                    "for_parameter_container_type: [ parameter_container_type.name: ${parameterContainerType.name} ]"
                )
                return DefaultDataSourceSpec<SI>(
                    schematicPath = schematicPath,
                    mappedSourceIndexAttempt =
                        @Suppress("UNCHECKED_CAST") //
                        (parameterContainerType as? SI).successIfNonNull(),
                    existingSchematicVertexOpt = existingSchematicVertex.some()
                )
            }
        }

        private data class DefaultDataSourceSpec<SI : SourceIndex<SI>>(
            val schematicPath: SchematicPath,
            val mappedSourceIndexAttempt: Try<SI>,
            val existingSchematicVertexOpt: Option<SchematicVertex> = none()
        ) : SchematicVertexFactory.DataSourceSpec<SI> {
            override fun onDataSource(dataSourceKey: DataSource.Key<SI>): Try<SchematicVertex> {
                logger.debug(
                    """on_data_source: [ source_type: ${dataSourceKey.dataSourceType}, 
                       |name: ${dataSourceKey.name} ]""".flattenIntoOneLine()
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
                val factory = DefaultSchematicVertexCreationFactory<SI>()
                return when (existingSchematicVertexOpt.orNull()) {
                    null -> {
                        factory.createNewSchematicVertexForSourceIndexOnDataSource(
                            schematicPath = schematicPath,
                            sourceIndex = mappedSourceIndexAttempt.orNull()!!,
                            dataSourceKey = dataSourceKey
                        )
                    }
                    else -> {
                        factory.updateExistingSchematicVertexWithSourceIndexOnDataSource(
                            schematicPath = schematicPath,
                            existingSchematicVertex = existingSchematicVertexOpt.orNull()!!,
                            sourceIndex = mappedSourceIndexAttempt.orNull()!!,
                            dataSourceKey = dataSourceKey
                        )
                    }
                }
            }
        }

        private class DefaultSchematicVertexCreationFactory<SI : SourceIndex<SI>> :
            SchematicVertexCreationTemplate<SI> {}
    }

    override fun createVertexForPath(
        schematicPath: SchematicPath
    ): SchematicVertexFactory.SourceIndexSpec {
        logger.debug("create_vertex_for_path: [ path: $schematicPath ]")
        return DefaultSourceIndexSpec(schematicPath = schematicPath)
    }
}

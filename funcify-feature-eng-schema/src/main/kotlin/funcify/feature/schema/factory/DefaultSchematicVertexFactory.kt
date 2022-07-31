package funcify.feature.schema.factory

import arrow.core.Option
import arrow.core.getOrElse
import arrow.core.none
import arrow.core.some
import funcify.feature.naming.ConventionalName
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.datasource.ParameterAttribute
import funcify.feature.schema.datasource.ParameterContainerType
import funcify.feature.schema.datasource.SourceAttribute
import funcify.feature.schema.datasource.SourceContainerType
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.schema.error.SchemaErrorResponse
import funcify.feature.schema.error.SchemaException
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.vertex.ParameterAttributeVertex
import funcify.feature.schema.vertex.ParameterContainerTypeVertex
import funcify.feature.schema.vertex.SourceAttributeVertex
import funcify.feature.schema.vertex.SourceContainerTypeVertex
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import funcify.feature.tools.extensions.TryExtensions.successIfNonNull
import org.slf4j.Logger

internal class DefaultSchematicVertexFactory : SchematicVertexFactory {

    companion object {

        private val logger: Logger = loggerFor<DefaultSchematicVertexFactory>()

        private data class DefaultNameSpec(val schematicPath: SchematicPath) :
            SchematicVertexFactory.NameSpec {

            override fun withName(
                conventionalName: ConventionalName
            ): SchematicVertexFactory.SourceIndexSpec {
                logger.debug("with_name: [ conventional_name: $conventionalName ]")
                return DefaultSourceIndexSpec(
                    schematicPath = schematicPath,
                    suppliedConventionalName = conventionalName.some()
                )
            }

            override fun extractingName(): SchematicVertexFactory.SourceIndexSpec {
                logger.debug("using_source_index_name: [ ]")
                return DefaultSourceIndexSpec(
                    schematicPath = schematicPath,
                    suppliedConventionalName = none()
                )
            }
        }

        private data class DefaultSourceIndexSpec(
            val schematicPath: SchematicPath,
            val suppliedConventionalName: Option<ConventionalName>
        ) : SchematicVertexFactory.SourceIndexSpec {

            override fun <SI : SourceIndex<SI>> forSourceAttribute(
                sourceAttribute: SourceAttribute<SI>
            ): Try<SchematicVertex> {
                logger.debug(
                    """for_source_attribute: [ source_attribute.
                        |conventional_name: ${sourceAttribute.name} 
                        |]""".flattenIntoOneLine()
                )
                return DefaultSchematicVertexSpec<SI>(
                        schematicPath = schematicPath,
                        conventionalName =
                            suppliedConventionalName.getOrElse { sourceAttribute.name },
                        mappedSourceIndexAttempt =
                            @Suppress("UNCHECKED_CAST") //
                            (sourceAttribute as? SI).successIfNonNull()
                    )
                    .createSchematicVertex()
            }

            override fun <SI : SourceIndex<SI>, A : SourceAttribute<SI>> forSourceContainerType(
                sourceContainerType: SourceContainerType<SI, A>
            ): Try<SchematicVertex> {
                logger.debug(
                    """for_source_container_type: [ source_container_type.
                        |conventional_name: ${sourceContainerType.name} ]
                        |""".flattenIntoOneLine()
                )
                return DefaultSchematicVertexSpec<SI>(
                        schematicPath = schematicPath,
                        conventionalName =
                            suppliedConventionalName.getOrElse { sourceContainerType.name },
                        mappedSourceIndexAttempt =
                            @Suppress("UNCHECKED_CAST") //
                            (sourceContainerType as? SI).successIfNonNull()
                    )
                    .createSchematicVertex()
            }

            override fun <SI : SourceIndex<SI>> forParameterAttribute(
                parameterAttribute: ParameterAttribute<SI>
            ): Try<SchematicVertex> {
                logger.debug(
                    """for_parameter_attribute: [ parameter_attribute.name: ${parameterAttribute.name} ]"""
                )
                return DefaultSchematicVertexSpec<SI>(
                        schematicPath = schematicPath,
                        conventionalName =
                            suppliedConventionalName.getOrElse { parameterAttribute.name },
                        mappedSourceIndexAttempt =
                            @Suppress("UNCHECKED_CAST") //
                            (parameterAttribute as? SI).successIfNonNull()
                    )
                    .createSchematicVertex()
            }

            override fun <
                SI : SourceIndex<SI>, A : ParameterAttribute<SI>> forParameterContainerType(
                parameterContainerType: ParameterContainerType<SI, A>
            ): Try<SchematicVertex> {
                logger.debug(
                    "for_parameter_container_type: [ parameter_container_type.name: ${parameterContainerType.name} ]"
                )
                return DefaultSchematicVertexSpec<SI>(
                        schematicPath = schematicPath,
                        conventionalName =
                            suppliedConventionalName.getOrElse { parameterContainerType.name },
                        mappedSourceIndexAttempt =
                            @Suppress("UNCHECKED_CAST") //
                            (parameterContainerType as? SI).successIfNonNull()
                    )
                    .createSchematicVertex()
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
                    suppliedConventionalName = suppliedConventionalName,
                    existingSchematicVertex = existingSchematicVertex
                )
            }
        }

        private data class DefaultExistingSchematicVertexSpec(
            val schematicPath: SchematicPath,
            val existingSchematicVertex: SchematicVertex,
            val suppliedConventionalName: Option<ConventionalName>
        ) : SchematicVertexFactory.ExistingSchematicVertexSpec {

            companion object {
                private fun SchematicVertex.name(): ConventionalName {
                    return when (this) {
                        is SourceAttributeVertex -> this.compositeAttribute.conventionalName
                        is ParameterAttributeVertex ->
                            this.compositeParameterAttribute.conventionalName
                        is SourceContainerTypeVertex -> this.compositeContainerType.conventionalName
                        is ParameterContainerTypeVertex ->
                            this.compositeParameterContainerType.conventionalName
                        else -> {
                            throw SchemaException(
                                SchemaErrorResponse.UNEXPECTED_ERROR,
                                "unsupported source index type: ${this::class.qualifiedName}"
                            )
                        }
                    }
                }
            }

            override fun <SI : SourceIndex<SI>> forSourceAttribute(
                sourceAttribute: SourceAttribute<SI>
            ): Try<SchematicVertex> {
                logger.debug(
                    """for_source_attribute: [ source_attribute.
                        |conventional_name: ${sourceAttribute.name} ]
                        |""".flattenIntoOneLine()
                )
                return DefaultSchematicVertexSpec<SI>(
                        schematicPath = schematicPath,
                        conventionalName =
                            suppliedConventionalName.getOrElse { existingSchematicVertex.name() },
                        mappedSourceIndexAttempt =
                            @Suppress("UNCHECKED_CAST") //
                            (sourceAttribute as? SI).successIfNonNull(),
                        existingSchematicVertexOpt = existingSchematicVertex.some()
                    )
                    .createSchematicVertex()
            }

            override fun <SI : SourceIndex<SI>, A : SourceAttribute<SI>> forSourceContainerType(
                sourceContainerType: SourceContainerType<SI, A>
            ): Try<SchematicVertex> {
                logger.debug(
                    """for_source_container_type: 
                        |[ source_container_type.conventional_name: 
                        |${sourceContainerType.name} ]""".flattenIntoOneLine()
                )
                return DefaultSchematicVertexSpec<SI>(
                        schematicPath = schematicPath,
                        conventionalName =
                            suppliedConventionalName.getOrElse { existingSchematicVertex.name() },
                        mappedSourceIndexAttempt =
                            @Suppress("UNCHECKED_CAST") //
                            (sourceContainerType as? SI).successIfNonNull(),
                        existingSchematicVertexOpt = existingSchematicVertex.some()
                    )
                    .createSchematicVertex()
            }

            override fun <SI : SourceIndex<SI>> forParameterAttribute(
                parameterAttribute: ParameterAttribute<SI>
            ): Try<SchematicVertex> {
                logger.debug(
                    """for_parameter_attribute: [ parameter_attribute.name: ${parameterAttribute.name} ]"""
                )
                return DefaultSchematicVertexSpec<SI>(
                        schematicPath = schematicPath,
                        conventionalName =
                            suppliedConventionalName.getOrElse { existingSchematicVertex.name() },
                        mappedSourceIndexAttempt =
                            @Suppress("UNCHECKED_CAST") //
                            (parameterAttribute as? SI).successIfNonNull(),
                        existingSchematicVertexOpt = existingSchematicVertex.some()
                    )
                    .createSchematicVertex()
            }

            override fun <
                SI : SourceIndex<SI>, A : ParameterAttribute<SI>> forParameterContainerType(
                parameterContainerType: ParameterContainerType<SI, A>
            ): Try<SchematicVertex> {
                logger.debug(
                    "for_parameter_container_type: [ parameter_container_type.name: ${parameterContainerType.name} ]"
                )
                return DefaultSchematicVertexSpec<SI>(
                        schematicPath = schematicPath,
                        conventionalName =
                            suppliedConventionalName.getOrElse { existingSchematicVertex.name() },
                        mappedSourceIndexAttempt =
                            @Suppress("UNCHECKED_CAST") //
                            (parameterContainerType as? SI).successIfNonNull(),
                        existingSchematicVertexOpt = existingSchematicVertex.some()
                    )
                    .createSchematicVertex()
            }
        }

        private data class DefaultSchematicVertexSpec<SI : SourceIndex<SI>>(
            val schematicPath: SchematicPath,
            val conventionalName: ConventionalName,
            val mappedSourceIndexAttempt: Try<SI>,
            val existingSchematicVertexOpt: Option<SchematicVertex> = none()
        ) {

            fun createSchematicVertex(): Try<SchematicVertex> {
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
                        factory.createNewSchematicVertexForSourceIndexWithName(
                            schematicPath = schematicPath,
                            sourceIndex = mappedSourceIndexAttempt.orNull()!!,
                            conventionalName = conventionalName
                        )
                    }
                    else -> {
                        factory.updateExistingSchematicVertexWithSourceIndexWithName(
                            schematicPath = schematicPath,
                            existingSchematicVertex = existingSchematicVertexOpt.orNull()!!,
                            sourceIndex = mappedSourceIndexAttempt.orNull()!!,
                            conventionalName = conventionalName
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
    ): SchematicVertexFactory.NameSpec {
        logger.debug("create_vertex_for_path: [ path: $schematicPath ]")
        return DefaultNameSpec(schematicPath = schematicPath)
    }
}

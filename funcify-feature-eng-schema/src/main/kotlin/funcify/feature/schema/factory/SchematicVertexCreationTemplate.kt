package funcify.feature.schema.factory

import arrow.core.Option
import arrow.core.getOrElse
import funcify.feature.naming.ConventionalName
import funcify.feature.naming.StandardNamingConventions
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.datasource.ParameterAttribute
import funcify.feature.schema.datasource.ParameterContainerType
import funcify.feature.schema.datasource.SourceAttribute
import funcify.feature.schema.datasource.SourceContainerType
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.schema.error.SchemaErrorResponse
import funcify.feature.schema.error.SchemaException
import funcify.feature.schema.index.DefaultCompositeParameterAttribute
import funcify.feature.schema.index.DefaultCompositeParameterContainerType
import funcify.feature.schema.index.DefaultCompositeSourceAttribute
import funcify.feature.schema.index.DefaultCompositeSourceContainerType
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.vertex.*
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.failure
import funcify.feature.tools.extensions.TryExtensions.successIfNonNull
import kotlin.reflect.KClass
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap

/**
 *
 * @author smccarron
 * @created 2022-07-15
 */
internal interface SchematicVertexCreationTemplate<SI : SourceIndex<SI>> {

    fun createNewSchematicVertexForSourceIndexWithName(
        schematicPath: SchematicPath,
        sourceIndex: SI,
        suppliedConventionalName: Option<ConventionalName>
    ): Try<SchematicVertex> {
        return when (sourceIndex) {
            is SourceContainerType<*, *> -> {
                @Suppress("UNCHECKED_CAST") //
                val targetTypeIndex: SourceContainerType<SI, *> =
                    sourceIndex as SourceContainerType<SI, *>
                createNewSchematicVertexForSourceContainerTypeWithName(
                    schematicPath,
                    targetTypeIndex,
                    suppliedConventionalName
                )
            }
            is SourceAttribute<*> -> {
                @Suppress("UNCHECKED_CAST") //
                val targetTypeIndex: SourceAttribute<SI> = sourceIndex as SourceAttribute<SI>
                createNewSchematicVertexForSourceAttributeWithName(
                    schematicPath,
                    targetTypeIndex,
                    suppliedConventionalName
                )
            }
            is ParameterContainerType<*, *> -> {
                @Suppress("UNCHECKED_CAST") //
                val targetTypeIndex: ParameterContainerType<SI, *> =
                    sourceIndex as ParameterContainerType<SI, *>
                createNewSchematicVertexForParameterContainerTypeWithName(
                    schematicPath,
                    targetTypeIndex,
                    suppliedConventionalName
                )
            }
            is ParameterAttribute<*> -> {
                @Suppress("UNCHECKED_CAST") //
                val targetTypeIndex: ParameterAttribute<SI> = sourceIndex as ParameterAttribute<SI>
                createNewSchematicVertexForParameterAttributeWithName(
                    schematicPath,
                    targetTypeIndex,
                    suppliedConventionalName
                )
            }
            else -> {
                val sourceIndexTypesSet: String =
                    sequenceOf<KClass<*>>(
                            SourceContainerType::class,
                            SourceAttribute::class,
                            ParameterContainerType::class,
                            ParameterAttribute::class
                        )
                        .joinToString(", ", "{ ", " }", transform = { it.qualifiedName ?: "<NA>" })
                SchemaException(
                        SchemaErrorResponse.UNEXPECTED_ERROR,
                        """unhandled source_index type: [ 
                            |expected: one of $sourceIndexTypesSet, 
                            |actual: ${sourceIndex::class.qualifiedName} 
                            |]""".flatten()
                    )
                    .failure()
            }
        }
    }

    fun updateExistingSchematicVertexWithSourceIndexWithName(
        schematicPath: SchematicPath,
        existingSchematicVertex: SchematicVertex,
        sourceIndex: SI,
        suppliedConventionalName: Option<ConventionalName>
    ): Try<SchematicVertex> {
        return when (sourceIndex) {
            is SourceContainerType<*, *> -> {
                @Suppress("UNCHECKED_CAST") //
                val targetType: SourceContainerType<SI, *> =
                    sourceIndex as SourceContainerType<SI, *>
                updateExistingSchematicVertexForSourceContainerTypeWithName(
                    schematicPath,
                    existingSchematicVertex,
                    targetType,
                    suppliedConventionalName
                )
            }
            is SourceAttribute<*> -> {
                @Suppress("UNCHECKED_CAST") //
                val targetType: SourceAttribute<SI> = sourceIndex as SourceAttribute<SI>
                updateExistingSchematicVertexForSourceAttributeWithName(
                    schematicPath,
                    existingSchematicVertex,
                    targetType,
                    suppliedConventionalName
                )
            }
            is ParameterContainerType<*, *> -> {
                @Suppress("UNCHECKED_CAST") //
                val targetType: ParameterContainerType<SI, *> =
                    sourceIndex as ParameterContainerType<SI, *>
                updateExistingSchematicVertexForParameterContainerTypeWithName(
                    schematicPath,
                    existingSchematicVertex,
                    targetType,
                    suppliedConventionalName
                )
            }
            is ParameterAttribute<*> -> {
                @Suppress("UNCHECKED_CAST") //
                val targetType: ParameterAttribute<SI> = sourceIndex as ParameterAttribute<SI>
                updateExistingSchematicVertexForParameterAttributeWithName(
                    schematicPath,
                    existingSchematicVertex,
                    targetType,
                    suppliedConventionalName
                )
            }
            else -> {
                val sourceIndexTypesSet: String =
                    sequenceOf<KClass<*>>(
                            SourceContainerType::class,
                            SourceAttribute::class,
                            ParameterContainerType::class,
                            ParameterAttribute::class
                        )
                        .joinToString(", ", "{ ", " }", transform = { it.qualifiedName ?: "<NA>" })
                SchemaException(
                        SchemaErrorResponse.UNEXPECTED_ERROR,
                        """unhandled source_index type: [ 
                            |expected: one of $sourceIndexTypesSet, 
                            |actual: ${sourceIndex::class.qualifiedName} 
                            |]""".flatten()
                    )
                    .failure()
            }
        }
    }

    fun <SA : SourceAttribute<SI>> createNewSchematicVertexForSourceContainerTypeWithName(
        schematicPath: SchematicPath,
        sourceContainerType: SourceContainerType<SI, SA>,
        suppliedConventionalName: Option<ConventionalName>
    ): Try<SchematicVertex> {
        return if (schematicPath.isRoot()) {
            DefaultSourceRootVertex(
                    path = schematicPath,
                    compositeContainerType =
                        DefaultCompositeSourceContainerType(
                            suppliedConventionalName.getOrElse { sourceContainerType.name },
                            persistentMapOf(
                                sourceContainerType.dataSourceLookupKey to sourceContainerType
                            )
                        )
                )
                .successIfNonNull()
        } else {
            DefaultSourceJunctionVertex(
                    path = schematicPath,
                    compositeContainerType =
                        DefaultCompositeSourceContainerType(
                            /**
                             * Another location where entity resolution and application of naming
                             * conventions can be done in the future
                             */
                            conventionalName =
                                suppliedConventionalName.getOrElse { sourceContainerType.name },
                            sourceContainerTypesByDataSource =
                                persistentMapOf(
                                    sourceContainerType.dataSourceLookupKey to sourceContainerType
                                )
                        ),
                    compositeAttribute =
                        DefaultCompositeSourceAttribute(
                            /**
                             * Another location where entity resolution and application of naming
                             * conventions can be done in the future
                             */
                            conventionalName =
                                StandardNamingConventions.CAMEL_CASE.deriveName(
                                    sourceContainerType.name.qualifiedForm
                                ),
                            sourceAttributesByDataSource = persistentMapOf()
                        )
                )
                .successIfNonNull()
        }
    }

    fun createNewSchematicVertexForSourceAttributeWithName(
        schematicPath: SchematicPath,
        sourceAttribute: SourceAttribute<SI>,
        conventionalName: Option<ConventionalName>
    ): Try<SchematicVertex> {
        return DefaultSourceLeafVertex(
                path = schematicPath,
                compositeAttribute =
                    DefaultCompositeSourceAttribute(
                        /**
                         * Location where entity resolution and application of naming conventions
                         * can be done in the future
                         */
                        conventionalName = conventionalName.getOrElse { sourceAttribute.name },
                        sourceAttributesByDataSource =
                            persistentMapOf(sourceAttribute.dataSourceLookupKey to sourceAttribute)
                    )
            )
            .successIfNonNull()
    }

    fun <PA : ParameterAttribute<SI>> createNewSchematicVertexForParameterContainerTypeWithName(
        schematicPath: SchematicPath,
        parameterContainerType: ParameterContainerType<SI, PA>,
        conventionalName: Option<ConventionalName>
    ): Try<SchematicVertex> {
        return DefaultParameterJunctionVertex(
                path = schematicPath,
                compositeParameterContainerType =
                    DefaultCompositeParameterContainerType(
                        conventionalName =
                            conventionalName.getOrElse { parameterContainerType.name },
                        parameterContainerTypeByDataSource =
                            persistentMapOf(
                                parameterContainerType.dataSourceLookupKey to parameterContainerType
                            )
                    ),
                compositeParameterAttribute =
                    DefaultCompositeParameterAttribute(
                        conventionalName =
                            StandardNamingConventions.CAMEL_CASE.deriveName(
                                parameterContainerType.name.qualifiedForm
                            ),
                        parameterAttributeByDataSource = persistentMapOf()
                    )
            )
            .successIfNonNull()
    }

    fun createNewSchematicVertexForParameterAttributeWithName(
        schematicPath: SchematicPath,
        parameterAttribute: ParameterAttribute<SI>,
        conventionalName: Option<ConventionalName>
    ): Try<SchematicVertex> {
        return DefaultParameterLeafVertex(
                path = schematicPath,
                compositeParameterAttribute =
                    DefaultCompositeParameterAttribute(
                        conventionalName = conventionalName.getOrElse { parameterAttribute.name },
                        parameterAttributeByDataSource =
                            persistentMapOf(
                                parameterAttribute.dataSourceLookupKey to parameterAttribute
                            )
                    )
            )
            .successIfNonNull()
    }

    fun <SA : SourceAttribute<SI>> updateExistingSchematicVertexForSourceContainerTypeWithName(
        schematicPath: SchematicPath,
        existingSchematicVertex: SchematicVertex,
        sourceContainerType: SourceContainerType<SI, SA>,
        conventionalName: Option<ConventionalName>
    ): Try<SchematicVertex> {
        return when (val existingVertex = existingSchematicVertex) {
            is SourceRootVertex ->
                DefaultSourceRootVertex(
                        path = schematicPath,
                        compositeContainerType =
                            DefaultCompositeSourceContainerType(
                                conventionalName =
                                    conventionalName.getOrElse { sourceContainerType.name },
                                sourceContainerTypesByDataSource =
                                    existingVertex.compositeContainerType
                                        .getSourceContainerTypeByDataSource()
                                        .toPersistentMap()
                                        .put(
                                            sourceContainerType.dataSourceLookupKey,
                                            sourceContainerType
                                        )
                            )
                    )
                    .successIfNonNull()
            is SourceLeafVertex ->
                DefaultSourceJunctionVertex(
                        path = schematicPath,
                        compositeContainerType =
                            DefaultCompositeSourceContainerType(
                                conventionalName =
                                    conventionalName.getOrElse { sourceContainerType.name },
                                sourceContainerTypesByDataSource =
                                    persistentMapOf(
                                        sourceContainerType.dataSourceLookupKey to
                                            sourceContainerType
                                    )
                            ),
                        compositeAttribute = existingVertex.compositeAttribute
                    )
                    .successIfNonNull()
            is SourceJunctionVertex ->
                DefaultSourceJunctionVertex(
                        path = schematicPath,
                        compositeContainerType =
                            DefaultCompositeSourceContainerType(
                                conventionalName =
                                    conventionalName.getOrElse { sourceContainerType.name },
                                sourceContainerTypesByDataSource =
                                    existingVertex.compositeContainerType
                                        .getSourceContainerTypeByDataSource()
                                        .toPersistentMap()
                                        .put(
                                            sourceContainerType.dataSourceLookupKey,
                                            sourceContainerType
                                        )
                            ),
                        compositeAttribute = existingVertex.compositeAttribute
                    )
                    .successIfNonNull()
            else -> {
                SchemaException(
                        SchemaErrorResponse.UNEXPECTED_ERROR,
                        """unhandled graph index type on existing vertex: [ 
                           |existing_parameter_vertex: { path: ${existingVertex.path}, type: ${existingVertex::class.qualifiedName} }, 
                           |input_source_container_type: { source_path: ${sourceContainerType.sourcePath}, name: ${sourceContainerType.name} }  
                           |]""".flatten()
                    )
                    .failure()
            }
        }
    }

    fun updateExistingSchematicVertexForSourceAttributeWithName(
        schematicPath: SchematicPath,
        existingSchematicVertex: SchematicVertex,
        sourceAttribute: SourceAttribute<SI>,
        conventionalName: Option<ConventionalName>
    ): Try<SchematicVertex> {
        return when (val existingVertex = existingSchematicVertex) {
            is SourceLeafVertex ->
                DefaultSourceLeafVertex(
                        path = schematicPath,
                        compositeAttribute =
                            DefaultCompositeSourceAttribute(
                                conventionalName =
                                    conventionalName.getOrElse { sourceAttribute.name },
                                sourceAttributesByDataSource =
                                    existingVertex.compositeAttribute
                                        .getSourceAttributeByDataSource()
                                        .toPersistentMap()
                                        .put(sourceAttribute.dataSourceLookupKey, sourceAttribute)
                            )
                    )
                    .successIfNonNull()
            is SourceJunctionVertex ->
                DefaultSourceJunctionVertex(
                        path = schematicPath,
                        compositeAttribute =
                            DefaultCompositeSourceAttribute(
                                conventionalName =
                                    conventionalName.getOrElse { sourceAttribute.name },
                                sourceAttributesByDataSource =
                                    existingVertex.compositeAttribute
                                        .getSourceAttributeByDataSource()
                                        .toPersistentMap()
                                        .put(sourceAttribute.dataSourceLookupKey, sourceAttribute)
                            ),
                        compositeContainerType = existingVertex.compositeContainerType
                    )
                    .successIfNonNull()
            else -> {
                SchemaException(
                        SchemaErrorResponse.UNEXPECTED_ERROR,
                        """unhandled graph index type on existing vertex: [ 
                           |existing_parameter_vertex: { path: ${existingVertex.path}, type: ${existingVertex::class.qualifiedName} }, 
                           |input_source_attribute: { source_path: ${sourceAttribute.sourcePath}, name: ${sourceAttribute.name} } 
                           |]""".flatten()
                    )
                    .failure()
            }
        }
    }

    fun <
        PA : ParameterAttribute<SI>> updateExistingSchematicVertexForParameterContainerTypeWithName(
        schematicPath: SchematicPath,
        existingSchematicVertex: SchematicVertex,
        parameterContainerType: ParameterContainerType<SI, PA>,
        conventionalName: Option<ConventionalName>
    ): Try<SchematicVertex> {
        return when (val existingVertex = existingSchematicVertex) {
            is ParameterJunctionVertex -> {
                DefaultParameterJunctionVertex(
                        existingVertex.path,
                        compositeParameterContainerType =
                            DefaultCompositeParameterContainerType(
                                conventionalName =
                                    conventionalName.getOrElse { parameterContainerType.name },
                                parameterContainerTypeByDataSource =
                                    existingVertex.compositeParameterContainerType
                                        .getParameterContainerTypeByDataSource()
                                        .toPersistentMap()
                                        .put(
                                            parameterContainerType.dataSourceLookupKey,
                                            parameterContainerType
                                        )
                            ),
                        compositeParameterAttribute = existingVertex.compositeParameterAttribute
                    )
                    .successIfNonNull()
            }
            is ParameterLeafVertex -> {
                DefaultParameterJunctionVertex(
                        existingVertex.path,
                        compositeParameterContainerType =
                            DefaultCompositeParameterContainerType(
                                conventionalName =
                                    conventionalName.getOrElse { parameterContainerType.name },
                                parameterContainerTypeByDataSource =
                                    persistentMapOf(
                                        parameterContainerType.dataSourceLookupKey to
                                            parameterContainerType
                                    )
                            ),
                        compositeParameterAttribute = existingVertex.compositeParameterAttribute
                    )
                    .successIfNonNull()
            }
            else -> {
                SchemaException(
                        SchemaErrorResponse.UNEXPECTED_ERROR,
                        """unhandled graph index type on existing vertex: [ 
                           |existing_parameter_vertex: { path: ${existingVertex.path}, type: ${existingVertex::class.qualifiedName} }, 
                           |input_parameter_container_type: { source_path: ${parameterContainerType.sourcePath}, name: ${parameterContainerType.name} } 
                           |]""".flatten()
                    )
                    .failure()
            }
        }
    }

    fun updateExistingSchematicVertexForParameterAttributeWithName(
        schematicPath: SchematicPath,
        existingSchematicVertex: SchematicVertex,
        parameterAttribute: ParameterAttribute<SI>,
        conventionalName: Option<ConventionalName>
    ): Try<SchematicVertex> {
        return when (val existingVertex = existingSchematicVertex) {
            is ParameterJunctionVertex -> {
                DefaultParameterJunctionVertex(
                        existingVertex.path,
                        compositeParameterContainerType =
                            existingVertex.compositeParameterContainerType,
                        compositeParameterAttribute =
                            DefaultCompositeParameterAttribute(
                                conventionalName =
                                    conventionalName.getOrElse { parameterAttribute.name },
                                parameterAttributeByDataSource =
                                    existingVertex.compositeParameterAttribute
                                        .getParameterAttributesByDataSource()
                                        .toPersistentMap()
                                        .put(
                                            parameterAttribute.dataSourceLookupKey,
                                            parameterAttribute
                                        )
                            )
                    )
                    .successIfNonNull()
            }
            is ParameterLeafVertex -> {
                DefaultParameterLeafVertex(
                        existingVertex.path,
                        compositeParameterAttribute =
                            DefaultCompositeParameterAttribute(
                                conventionalName =
                                    conventionalName.getOrElse { parameterAttribute.name },
                                parameterAttributeByDataSource =
                                    existingVertex.compositeParameterAttribute
                                        .getParameterAttributesByDataSource()
                                        .toPersistentMap()
                                        .put(
                                            parameterAttribute.dataSourceLookupKey,
                                            parameterAttribute
                                        )
                            )
                    )
                    .successIfNonNull()
            }
            else -> {
                SchemaException(
                        SchemaErrorResponse.UNEXPECTED_ERROR,
                        """unhandled graph index type on existing vertex: [ 
                           |existing_parameter_vertex: { path: ${existingVertex.path}, type: ${existingVertex::class.qualifiedName} }, 
                           |input_parameter_attribute: { source_path: ${parameterAttribute.sourcePath}, name: ${parameterAttribute.name} } 
                           |]""".flatten()
                    )
                    .failure()
            }
        }
    }
}

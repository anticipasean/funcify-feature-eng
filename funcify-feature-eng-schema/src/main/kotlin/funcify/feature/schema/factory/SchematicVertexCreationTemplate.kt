package funcify.feature.schema.factory

import funcify.feature.naming.StandardNamingConventions
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.datasource.DataSource
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
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
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

    fun createNewSchematicVertexForSourceIndexOnDataSource(
        schematicPath: SchematicPath,
        sourceIndex: SI,
        dataSource: DataSource<SI>
    ): Try<SchematicVertex> {
        return when (sourceIndex) {
            is SourceContainerType<*, *> -> {
                @Suppress("UNCHECKED_CAST") //
                val targetTypeIndex: SourceContainerType<SI, *> =
                    sourceIndex as SourceContainerType<SI, *>
                createNewSchematicVertexForSourceContainerTypeOnDataSource(
                    schematicPath,
                    targetTypeIndex,
                    dataSource
                )
            }
            is SourceAttribute<*> -> {
                @Suppress("UNCHECKED_CAST") //
                val targetTypeIndex: SourceAttribute<SI> = sourceIndex as SourceAttribute<SI>
                createNewSchematicVertexForSourceAttributeOnDataSource(
                    schematicPath,
                    targetTypeIndex,
                    dataSource
                )
            }
            is ParameterContainerType<*, *> -> {
                @Suppress("UNCHECKED_CAST") //
                val targetTypeIndex: ParameterContainerType<SI, *> =
                    sourceIndex as ParameterContainerType<SI, *>
                createNewSchematicVertexForParameterContainerTypeOnDataSource(
                    schematicPath,
                    targetTypeIndex,
                    dataSource
                )
            }
            is ParameterAttribute<*> -> {
                @Suppress("UNCHECKED_CAST") //
                val targetTypeIndex: ParameterAttribute<SI> = sourceIndex as ParameterAttribute<SI>
                createNewSchematicVertexForParameterAttributeOnDataSource(
                    schematicPath,
                    targetTypeIndex,
                    dataSource
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
                            |]""".flattenIntoOneLine()
                    )
                    .failure()
            }
        }
    }

    fun updateExistingSchematicVertexWithSourceIndexOnDataSource(
        schematicPath: SchematicPath,
        existingSchematicVertex: SchematicVertex,
        sourceIndex: SI,
        dataSource: DataSource<SI>
    ): Try<SchematicVertex> {
        return when (sourceIndex) {
            is SourceContainerType<*, *> -> {
                @Suppress("UNCHECKED_CAST") //
                val targetType: SourceContainerType<SI, *> =
                    sourceIndex as SourceContainerType<SI, *>
                updateExistingSchematicVertexForSourceContainerTypeOnDataSource(
                    schematicPath,
                    existingSchematicVertex,
                    targetType,
                    dataSource
                )
            }
            is SourceAttribute<*> -> {
                @Suppress("UNCHECKED_CAST") //
                val targetType: SourceAttribute<SI> = sourceIndex as SourceAttribute<SI>
                updateExistingSchematicVertexForSourceAttributeOnDataSource(
                    schematicPath,
                    existingSchematicVertex,
                    targetType,
                    dataSource
                )
            }
            is ParameterContainerType<*, *> -> {
                @Suppress("UNCHECKED_CAST") //
                val targetType: ParameterContainerType<SI, *> =
                    sourceIndex as ParameterContainerType<SI, *>
                updateExistingSchematicVertexForParameterContainerTypeOnDataSource(
                    schematicPath,
                    existingSchematicVertex,
                    targetType,
                    dataSource
                )
            }
            is ParameterAttribute<*> -> {
                @Suppress("UNCHECKED_CAST") //
                val targetType: ParameterAttribute<SI> = sourceIndex as ParameterAttribute<SI>
                updateExistingSchematicVertexForParameterAttributeOnDataSource(
                    schematicPath,
                    existingSchematicVertex,
                    targetType,
                    dataSource
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
                            |]""".flattenIntoOneLine()
                    )
                    .failure()
            }
        }
    }

    fun <SA : SourceAttribute<SI>> createNewSchematicVertexForSourceContainerTypeOnDataSource(
        schematicPath: SchematicPath,
        sourceContainerType: SourceContainerType<SI, SA>,
        dataSource: DataSource<SI>
    ): Try<SchematicVertex> {
        return if (schematicPath.isRoot()) {
            DefaultSourceRootVertex(
                    path = schematicPath,
                    compositeContainerType =
                        DefaultCompositeSourceContainerType(
                            sourceContainerType.name,
                            persistentMapOf(dataSource.key to sourceContainerType)
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
                            conventionalName = sourceContainerType.name,
                            sourceContainerTypesByDataSource =
                                persistentMapOf(dataSource.key to sourceContainerType)
                        ),
                    compositeAttribute =
                        DefaultCompositeSourceAttribute(
                            /**
                             * Another location where entity resolution and application of naming
                             * conventions can be done in the future
                             */
                            conventionalName =
                                StandardNamingConventions.CAMEL_CASE.deriveName(
                                    sourceContainerType.name.toString()
                                ),
                            sourceAttributesByDataSource = persistentMapOf()
                        )
                )
                .successIfNonNull()
        }
    }

    fun createNewSchematicVertexForSourceAttributeOnDataSource(
        schematicPath: SchematicPath,
        sourceAttribute: SourceAttribute<SI>,
        dataSource: DataSource<SI>
    ): Try<SchematicVertex> {
        return DefaultSourceLeafVertex(
                path = schematicPath,
                compositeAttribute =
                    DefaultCompositeSourceAttribute(
                        /**
                         * Location where entity resolution and application of naming conventions
                         * can be done in the future
                         */
                        conventionalName = sourceAttribute.name,
                        sourceAttributesByDataSource =
                            persistentMapOf(dataSource.key to sourceAttribute)
                    )
            )
            .successIfNonNull()
    }

    fun <PA : ParameterAttribute<SI>> createNewSchematicVertexForParameterContainerTypeOnDataSource(
        schematicPath: SchematicPath,
        parameterContainerType: ParameterContainerType<SI, PA>,
        dataSource: DataSource<SI>
    ): Try<SchematicVertex> {
        return DefaultParameterJunctionVertex(
                path = schematicPath,
                compositeParameterContainerType =
                    DefaultCompositeParameterContainerType(
                        conventionalName = parameterContainerType.name,
                        parameterContainerTypeByDataSource =
                            persistentMapOf(dataSource.key to parameterContainerType)
                    ),
                compositeParameterAttribute =
                    DefaultCompositeParameterAttribute(
                        conventionalName =
                            StandardNamingConventions.CAMEL_CASE.deriveName(
                                parameterContainerType.name.toString()
                            ),
                        parameterAttributeByDataSource = persistentMapOf()
                    )
            )
            .successIfNonNull()
    }

    fun createNewSchematicVertexForParameterAttributeOnDataSource(
        schematicPath: SchematicPath,
        parameterAttribute: ParameterAttribute<SI>,
        dataSource: DataSource<SI>
    ): Try<SchematicVertex> {
        return DefaultParameterLeafVertex(
                path = schematicPath,
                compositeParameterAttribute =
                    DefaultCompositeParameterAttribute(
                        conventionalName = parameterAttribute.name,
                        parameterAttributeByDataSource =
                            persistentMapOf(dataSource.key to parameterAttribute)
                    )
            )
            .successIfNonNull()
    }

    fun <SA : SourceAttribute<SI>> updateExistingSchematicVertexForSourceContainerTypeOnDataSource(
        schematicPath: SchematicPath,
        existingSchematicVertex: SchematicVertex,
        sourceContainerType: SourceContainerType<SI, SA>,
        dataSource: DataSource<SI>
    ): Try<SchematicVertex> {
        return when (val existingVertex = existingSchematicVertex) {
            is SourceRootVertex ->
                DefaultSourceRootVertex(
                        path = schematicPath,
                        compositeContainerType =
                            DefaultCompositeSourceContainerType(
                                conventionalName =
                                    existingVertex.compositeContainerType.conventionalName,
                                sourceContainerTypesByDataSource =
                                    existingVertex.compositeContainerType
                                        .getSourceContainerTypeByDataSource()
                                        .toPersistentMap()
                                        .put(dataSource.key, sourceContainerType)
                            )
                    )
                    .successIfNonNull()
            is SourceLeafVertex ->
                DefaultSourceJunctionVertex(
                        path = schematicPath,
                        compositeContainerType =
                            DefaultCompositeSourceContainerType(
                                conventionalName =
                                    existingVertex.compositeAttribute.conventionalName,
                                sourceContainerTypesByDataSource =
                                    persistentMapOf(dataSource.key to sourceContainerType)
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
                                    existingVertex.compositeContainerType.conventionalName,
                                sourceContainerTypesByDataSource =
                                    existingVertex.compositeContainerType
                                        .getSourceContainerTypeByDataSource()
                                        .toPersistentMap()
                                        .put(dataSource.key, sourceContainerType)
                            ),
                        compositeAttribute = existingVertex.compositeAttribute
                    )
                    .successIfNonNull()
            else -> {
                SchemaException(
                        SchemaErrorResponse.UNEXPECTED_ERROR,
                        "unhandled graph index type on existing vertex: ${existingVertex::class.qualifiedName}"
                    )
                    .failure()
            }
        }
    }

    fun updateExistingSchematicVertexForSourceAttributeOnDataSource(
        schematicPath: SchematicPath,
        existingSchematicVertex: SchematicVertex,
        sourceAttribute: SourceAttribute<SI>,
        dataSource: DataSource<SI>
    ): Try<SchematicVertex> {
        return when (val existingVertex = existingSchematicVertex) {
            is SourceLeafVertex ->
                DefaultSourceLeafVertex(
                        path = schematicPath,
                        compositeAttribute =
                            DefaultCompositeSourceAttribute(
                                conventionalName =
                                    existingVertex.compositeAttribute.conventionalName,
                                existingVertex.compositeAttribute
                                    .getSourceAttributeByDataSource()
                                    .toPersistentMap()
                                    .put(dataSource.key, sourceAttribute)
                            )
                    )
                    .successIfNonNull()
            is SourceJunctionVertex ->
                DefaultSourceJunctionVertex(
                        path = schematicPath,
                        compositeAttribute =
                            DefaultCompositeSourceAttribute(
                                conventionalName =
                                    existingVertex.compositeAttribute.conventionalName,
                                sourceAttributesByDataSource =
                                    existingVertex.compositeAttribute
                                        .getSourceAttributeByDataSource()
                                        .toPersistentMap()
                                        .put(dataSource.key, sourceAttribute)
                            ),
                        compositeContainerType = existingVertex.compositeContainerType
                    )
                    .successIfNonNull()
            else -> {
                SchemaException(
                        SchemaErrorResponse.UNEXPECTED_ERROR,
                        "unhandled graph index type on existing vertex: ${existingVertex::class.qualifiedName}"
                    )
                    .failure()
            }
        }
    }

    fun <
        PA : ParameterAttribute<SI>
    > updateExistingSchematicVertexForParameterContainerTypeOnDataSource(
        schematicPath: SchematicPath,
        existingSchematicVertex: SchematicVertex,
        parameterContainerType: ParameterContainerType<SI, PA>,
        dataSource: DataSource<SI>
    ): Try<SchematicVertex> {
        return when (val existingVertex = existingSchematicVertex) {
            is ParameterJunctionVertex -> {
                DefaultParameterJunctionVertex(
                        existingVertex.path,
                        compositeParameterContainerType =
                            DefaultCompositeParameterContainerType(
                                conventionalName =
                                    existingVertex.compositeParameterContainerType.conventionalName,
                                parameterContainerTypeByDataSource =
                                    existingVertex.compositeParameterContainerType
                                        .getParameterContainerTypeByDataSource()
                                        .toPersistentMap()
                                        .put(dataSource.key, parameterContainerType)
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
                                conventionalName = parameterContainerType.name,
                                parameterContainerTypeByDataSource =
                                    persistentMapOf(dataSource.key to parameterContainerType)
                            ),
                        compositeParameterAttribute = existingVertex.compositeParameterAttribute
                    )
                    .successIfNonNull()
            }
            else -> {
                SchemaException(
                        SchemaErrorResponse.UNEXPECTED_ERROR,
                        "unhandled graph index type on existing vertex: ${existingVertex::class.qualifiedName}"
                    )
                    .failure()
            }
        }
    }

    fun updateExistingSchematicVertexForParameterAttributeOnDataSource(
        schematicPath: SchematicPath,
        existingSchematicVertex: SchematicVertex,
        parameterAttribute: ParameterAttribute<SI>,
        dataSource: DataSource<SI>
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
                                    existingVertex.compositeParameterAttribute.conventionalName,
                                parameterAttributeByDataSource =
                                    existingVertex.compositeParameterAttribute
                                        .getParameterAttributesByDataSource()
                                        .toPersistentMap()
                                        .put(dataSource.key, parameterAttribute)
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
                                    existingVertex.compositeParameterAttribute.conventionalName,
                                parameterAttributeByDataSource =
                                    existingVertex.compositeParameterAttribute
                                        .getParameterAttributesByDataSource()
                                        .toPersistentMap()
                                        .put(dataSource.key, parameterAttribute)
                            )
                    )
                    .successIfNonNull()
            }
            else -> {
                SchemaException(
                        SchemaErrorResponse.UNEXPECTED_ERROR,
                        "unhandled graph index type on existing vertex: ${existingVertex::class.qualifiedName}"
                    )
                    .failure()
            }
        }
    }
}

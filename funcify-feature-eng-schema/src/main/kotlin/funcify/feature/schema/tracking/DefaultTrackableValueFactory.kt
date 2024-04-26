package funcify.feature.schema.tracking

import arrow.core.Option
import arrow.core.foldLeft
import arrow.core.orElse
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import funcify.feature.error.ServiceError
import funcify.feature.schema.path.lookup.SchematicPath
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.tracking.TrackableValue.CalculatedValue
import funcify.feature.schema.tracking.TrackableValue.PlannedValue
import funcify.feature.schema.tracking.TrackableValue.TrackedValue
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.container.attempt.Try.Companion.filterInstanceOf
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.successIfNonNull
import graphql.schema.GraphQLOutputType
import graphql.schema.GraphQLTypeUtil
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.collections.immutable.toPersistentSet
import java.time.Instant

/**
 * @author smccarron
 * @created 2022-09-05
 */
internal class DefaultTrackableValueFactory : TrackableValueFactory {

    companion object {

        internal abstract class BaseBuilder<B : TrackableValue.Builder<B>, V>(
            protected open var operationPath: GQLOperationPath? = null,
            protected open var contextualParameters: PersistentMap.Builder<String, JsonNode> =
                persistentMapOf<String, JsonNode>().builder(),
            protected open var graphQLOutputType: GraphQLOutputType? = null,
        ) : TrackableValue.Builder<B> {

            companion object {
                /**
                 * @param WB - Wide Builder Type
                 * @param NB - Narrow Builder Type
                 */
                private inline fun <reified WB, reified NB : WB> WB.applyOnBuilder(
                    builderUpdater: WB.() -> Unit
                ): NB {
                    return this.apply(builderUpdater) as NB
                }
            }

            override fun operationPath(operationPath: GQLOperationPath): B =
                this.applyOnBuilder { this.operationPath = operationPath }

            override fun setContextualParameters(parameters: Map<String, JsonNode>): B =
                this.applyOnBuilder {
                    this.contextualParameters = parameters.toPersistentMap().builder()
                }

            override fun addAllContextualParameters(
                contextualParameters: Map<String, JsonNode>
            ): B = this.applyOnBuilder { this.contextualParameters.putAll(contextualParameters) }

            override fun addContextualParameter(parameter: Pair<String, JsonNode>): B =
                this.applyOnBuilder {
                    this.contextualParameters.put(parameter.first, parameter.second)
                }

            override fun clearContextualParameters(): B =
                this.applyOnBuilder { this.contextualParameters.clear() }

            override fun addContextualParameter(
                parameterName: String,
                parameterValue: JsonNode
            ): B =
                this.applyOnBuilder { this.contextualParameters.put(parameterName, parameterValue) }

            override fun removeContextualParameter(parameterName: String): B =
                this.applyOnBuilder { this.contextualParameters.remove(parameterName) }

            override fun graphQLOutputType(graphQLOutputType: GraphQLOutputType): B =
                this.applyOnBuilder { this.graphQLOutputType = graphQLOutputType }
        }

        internal class DefaultPlannedValueBuilder<V>(
            private val existingPlannedValue: PlannedValue<V>? = null
        ) :
            BaseBuilder<PlannedValue.Builder, V>(
                operationPath = existingPlannedValue?.operationPath,
                contextualParameters =
                    existingPlannedValue?.contextualParameters?.toPersistentMap()?.builder()
                        ?: persistentMapOf<String, JsonNode>().builder(),
                graphQLOutputType = existingPlannedValue?.graphQLOutputType
            ),
            PlannedValue.Builder {

            override fun <V> buildForInstanceOf(): Try<PlannedValue<V>> {
                return when {
                    operationPath == null -> {
                        Try.failure(
                            ServiceError.of("operation_path has not been set for planned_value")
                        )
                    }
                    // contextualParameters.isEmpty() -> {
                    //    Try.failure(
                    //        ServiceError.of(
                    //            """at least one contextual_parameter
                    //                |must be provided for association and/or identification
                    //                |of the planned_value"""
                    //                .flatten()
                    //        )
                    //    )
                    // }
                    graphQLOutputType == null -> {
                        Try.failure(
                            ServiceError.of(
                                """graphql_output_type must be provided for planned_value"""
                                    .flatten()
                            )
                        )
                    }
                    else -> {
                        DefaultPlannedValue<V>(
                                operationPath = operationPath!!,
                                contextualParameters = contextualParameters.build(),
                                graphQLOutputType = graphQLOutputType!!,
                            )
                            .successIfNonNull()
                    }
                }
            }
        }

        internal class DefaultCalculatedValueBuilder<V>(
            private val existingPlannedValue: PlannedValue<V>? = null,
            override var operationPath: GQLOperationPath? = existingPlannedValue?.operationPath,
            override var contextualParameters: PersistentMap.Builder<String, JsonNode> =
                existingPlannedValue?.contextualParameters?.toPersistentMap()?.builder()
                    ?: persistentMapOf<String, JsonNode>().builder(),
            override var graphQLOutputType: GraphQLOutputType? =
                existingPlannedValue?.graphQLOutputType,
            private var calculatedValue: V? = null,
            private var calculatedTimestamp: Instant? = null,
        ) :
            BaseBuilder<CalculatedValue.Builder<V>, V>(
                operationPath = operationPath,
                contextualParameters = contextualParameters,
                graphQLOutputType = graphQLOutputType
            ),
            CalculatedValue.Builder<V> {

            override fun calculatedValue(calculatedValue: V): DefaultCalculatedValueBuilder<V> {
                this.calculatedValue = calculatedValue
                return this
            }

            override fun calculatedTimestamp(
                calculatedTimestamp: Instant
            ): DefaultCalculatedValueBuilder<V> {
                this.calculatedTimestamp = calculatedTimestamp
                return this
            }

            override fun build(): Try<CalculatedValue<V>> {
                return when {
                    operationPath == null -> {
                        Try.failure(
                            ServiceError.of("operation_path has not been set for calculated_value")
                        )
                    }
                    // contextualParameters.isEmpty() -> {
                    //    Try.failure(
                    //        ServiceError.of(
                    //            """at least one contextual_parameter
                    //                |must be provided for association and/or identification
                    //                |of the calculated_value"""
                    //                .flatten()
                    //        )
                    //    )
                    // }
                    graphQLOutputType == null -> {
                        Try.failure(
                            ServiceError.of(
                                """graphql_output_type must be provided for calculated_value"""
                                    .flatten()
                            )
                        )
                    }
                    calculatedValue == null || calculatedTimestamp == null -> {
                        Try.failure(
                            ServiceError.of(
                                """calculated_value and calculated_timestamp 
                                    |must be provided for calculated_value creation"""
                                    .flatten()
                            )
                        )
                    }
                    else -> {
                        DefaultCalculatedValue<V>(
                                operationPath!!,
                                contextualParameters.build(),
                                graphQLOutputType!!,
                                calculatedValue!!,
                                calculatedTimestamp!!
                            )
                            .successIfNonNull()
                    }
                }
            }
        }

        internal class DefaultTrackedValueBuilder<V>(
            private val existingPlannedValue: PlannedValue<V>? = null,
            private val existingCalculatedValue: CalculatedValue<V>? = null,
            override var operationPath: GQLOperationPath? =
                existingPlannedValue
                    .toOption()
                    .map(PlannedValue<V>::operationPath)
                    .orElse {
                        existingCalculatedValue.toOption().map(CalculatedValue<V>::operationPath)
                    }
                    .orNull(),
            override var contextualParameters: PersistentMap.Builder<String, JsonNode> =
                existingPlannedValue
                    .toOption()
                    .map(PlannedValue<V>::contextualParameters)
                    .orElse {
                        existingCalculatedValue
                            .toOption()
                            .map(CalculatedValue<V>::contextualParameters)
                    }
                    .map { m -> m.toPersistentMap().builder() }
                    .orNull() ?: persistentMapOf<String, JsonNode>().builder(),
            override var graphQLOutputType: GraphQLOutputType? =
                existingPlannedValue
                    .toOption()
                    .map(PlannedValue<V>::graphQLOutputType)
                    .orElse {
                        existingCalculatedValue
                            .toOption()
                            .map(CalculatedValue<V>::graphQLOutputType)
                    }
                    .orNull(),
            private var canonicalPath: SchematicPath? = null,
            private var referencePaths: PersistentSet.Builder<SchematicPath> =
                persistentSetOf<SchematicPath>().builder(),
            private var trackedValue: V? = null,
            private var valueAtTimestamp: Instant? = null,
        ) :
            BaseBuilder<TrackedValue.Builder<V>, V>(
                operationPath = operationPath,
                contextualParameters = contextualParameters,
                graphQLOutputType = graphQLOutputType
            ),
            TrackedValue.Builder<V> {

            override fun canonicalPath(
                canonicalPath: SchematicPath
            ): DefaultTrackedValueBuilder<V> {
                this.canonicalPath = canonicalPath
                return this
            }

            override fun referencePaths(
                referencePaths: ImmutableSet<SchematicPath>
            ): DefaultTrackedValueBuilder<V> {
                this.referencePaths = referencePaths.toPersistentSet().builder()
                return this
            }

            override fun addReferencePath(
                referencePath: SchematicPath
            ): DefaultTrackedValueBuilder<V> {
                this.referencePaths.add(referencePath)
                return this
            }

            override fun removeReferencePath(
                referencePath: SchematicPath
            ): DefaultTrackedValueBuilder<V> {
                this.referencePaths.remove(referencePath)
                return this
            }

            override fun clearReferencePaths(): DefaultTrackedValueBuilder<V> {
                this.referencePaths.clear()
                return this
            }

            override fun addReferencePaths(
                referencePaths: Iterable<SchematicPath>
            ): DefaultTrackedValueBuilder<V> {
                this.referencePaths.addAll(referencePaths)
                return this
            }

            override fun trackedValue(trackedValue: V): DefaultTrackedValueBuilder<V> {
                this.trackedValue = trackedValue
                return this
            }

            override fun valueAtTimestamp(
                valueAtTimestamp: Instant
            ): DefaultTrackedValueBuilder<V> {
                this.valueAtTimestamp = valueAtTimestamp
                return this
            }

            override fun build(): Try<TrackedValue<V>> {
                return when {
                    operationPath == null -> {
                        Try.failure(
                            ServiceError.of("operation_path has not been set for tracked_value")
                        )
                    }
                    // contextualParameters.isEmpty() -> {
                    //    Try.failure(
                    //        ServiceError.of(
                    //            """at least one contextual_parameter
                    //                |must be provided for association and/or identification
                    //                |of the tracked_value"""
                    //                .flatten()
                    //        )
                    //    )
                    // }
                    graphQLOutputType == null -> {
                        Try.failure(
                            ServiceError.of(
                                """graphql_output_type must be provided for tracked_value"""
                                    .flatten()
                            )
                        )
                    }
                    trackedValue == null || valueAtTimestamp == null -> {
                        Try.failure(
                            ServiceError.of(
                                """both a tracked_value and value_at_timestamp 
                                |must be provided for creation of a tracked_value"""
                                    .flatten()
                            )
                        )
                    }
                    else -> {
                        DefaultTrackedValue<V>(
                                operationPath!!,
                                canonicalPath.toOption(),
                                referencePaths.build(),
                                contextualParameters.build(),
                                graphQLOutputType!!,
                                trackedValue!!,
                                valueAtTimestamp!!
                            )
                            .successIfNonNull()
                    }
                }
            }
        }

        internal data class DefaultPlannedValue<V>(
            override val operationPath: GQLOperationPath,
            override val contextualParameters: ImmutableMap<String, JsonNode>,
            override val graphQLOutputType: GraphQLOutputType
        ) : PlannedValue<V> {

            override fun update(
                transformer: PlannedValue.Builder.() -> PlannedValue.Builder
            ): PlannedValue<V> {
                @Suppress("UNCHECKED_CAST") //
                return transformer(DefaultPlannedValueBuilder<V>(existingPlannedValue = this))
                    .buildForInstanceOf<V>()
                    .orElse(this)
            }

            override fun transitionToCalculated(
                mapper: CalculatedValue.Builder<V>.() -> CalculatedValue.Builder<V>
            ): TrackableValue<V> {
                @Suppress("UNCHECKED_CAST") //
                return mapper(DefaultCalculatedValueBuilder<V>(existingPlannedValue = this))
                    .build()
                    .filterInstanceOf<TrackableValue<V>>()
                    .orElseGet { this }
            }

            override fun transitionToTracked(
                mapper: TrackedValue.Builder<V>.() -> TrackedValue.Builder<V>
            ): TrackableValue<V> {
                return mapper(DefaultTrackedValueBuilder<V>(existingPlannedValue = this))
                    .build()
                    .filterInstanceOf<TrackableValue<V>>()
                    .orElseGet { this }
            }

            private val internedStringRep: String by lazy {
                mapOf(
                        "type" to PlannedValue::class.qualifiedName,
                        "operationPath" to operationPath,
                        "contextualParameters" to
                            contextualParameters.asSequence().fold(
                                JsonNodeFactory.instance.objectNode()
                            ) { on, (k, v) ->
                                on.set(k, v)
                            },
                        "graphQLOutputType" to GraphQLTypeUtil.simplePrint(graphQLOutputType)
                    )
                    .foldLeft(JsonNodeFactory.instance.objectNode()) { on, (k, v) ->
                        when (v) {
                            is JsonNode -> {
                                on.set<ObjectNode>(k, v)
                            }
                            else -> {
                                on.put(k, v.toString())
                            }
                        }
                    }
                    .toString()
            }

            override fun toString(): String {
                return internedStringRep
            }
        }

        internal data class DefaultCalculatedValue<V>(
            override val operationPath: GQLOperationPath,
            override val contextualParameters: ImmutableMap<String, JsonNode>,
            override val graphQLOutputType: GraphQLOutputType,
            override val calculatedValue: V,
            override val calculatedTimestamp: Instant
        ) : CalculatedValue<V> {

            override fun update(
                transformer: CalculatedValue.Builder<V>.() -> CalculatedValue.Builder<V>
            ): CalculatedValue<V> {
                return transformer(
                        DefaultCalculatedValueBuilder<V>(
                            existingPlannedValue = null,
                            operationPath = operationPath,
                            contextualParameters = contextualParameters.toPersistentMap().builder(),
                            graphQLOutputType = graphQLOutputType,
                            calculatedValue = calculatedValue,
                            calculatedTimestamp = calculatedTimestamp
                        )
                    )
                    .build()
                    .orElse(this)
            }

            override fun transitionToTracked(
                mapper: TrackedValue.Builder<V>.() -> TrackedValue.Builder<V>
            ): TrackableValue<V> {
                return mapper(DefaultTrackedValueBuilder<V>(null, this))
                    .build()
                    .filterInstanceOf<TrackableValue<V>>()
                    .orElse(this)
            }

            private val internedStringRep: String by lazy {
                mapOf(
                        "type" to CalculatedValue::class.qualifiedName,
                        "operationPath" to operationPath,
                        "contextualParameters" to
                            contextualParameters.asSequence().fold(
                                JsonNodeFactory.instance.objectNode()
                            ) { on, (k, v) ->
                                on.set(k, v)
                            },
                        "graphQLOutputType" to GraphQLTypeUtil.simplePrint(graphQLOutputType),
                        "calculatedValue" to calculatedValue,
                        "calculatedTimestamp" to calculatedTimestamp
                    )
                    .foldLeft(JsonNodeFactory.instance.objectNode()) { on, (k, v) ->
                        when (v) {
                            is JsonNode -> {
                                on.set<ObjectNode>(k, v)
                            }
                            else -> {
                                on.put(k, v.toString())
                            }
                        }
                    }
                    .toString()
            }

            override fun toString(): String {
                return internedStringRep
            }
        }

        internal data class DefaultTrackedValue<V>(
            override val operationPath: GQLOperationPath,
            override val canonicalPath: Option<SchematicPath>,
            override val referencePaths: ImmutableSet<SchematicPath>,
            override val contextualParameters: ImmutableMap<String, JsonNode>,
            override val graphQLOutputType: GraphQLOutputType,
            override val trackedValue: V,
            override val valueAtTimestamp: Instant
        ) : TrackedValue<V> {

            override fun update(
                transformer: TrackedValue.Builder<V>.() -> TrackedValue.Builder<V>
            ): TrackedValue<V> {

                return transformer(
                        DefaultTrackedValueBuilder<V>(
                            null,
                            null,
                            operationPath,
                            contextualParameters.toPersistentMap().builder(),
                            graphQLOutputType,
                            canonicalPath.orNull(),
                            referencePaths.toPersistentSet().builder(),
                            trackedValue,
                            valueAtTimestamp
                        )
                    )
                    .build()
                    .orElse(this)
            }

            private val internedStringRep: String by lazy {
                mapOf(
                        "type" to TrackedValue::class.qualifiedName,
                        "operationPath" to operationPath,
                        "contextualParameters" to
                            contextualParameters.asSequence().fold(
                                JsonNodeFactory.instance.objectNode()
                            ) { on, (k, v) ->
                                on.set(k, v)
                            },
                        "graphQLOutputType" to GraphQLTypeUtil.simplePrint(graphQLOutputType),
                        "trackedValue" to trackedValue,
                        "valueAtTimestamp" to valueAtTimestamp
                    )
                    .foldLeft(JsonNodeFactory.instance.objectNode()) { on, (k, v) ->
                        when (v) {
                            is JsonNode -> {
                                on.set<ObjectNode>(k, v)
                            }
                            else -> {
                                on.put(k, v.toString())
                            }
                        }
                    }
                    .toString()
            }

            override fun toString(): String {
                return internedStringRep
            }
        }
    }

    override fun builder(): PlannedValue.Builder {
        return DefaultPlannedValueBuilder<Any?>()
    }
}

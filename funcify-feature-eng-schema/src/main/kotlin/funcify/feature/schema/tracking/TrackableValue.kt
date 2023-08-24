package funcify.feature.schema.tracking

import arrow.core.Option
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.tools.container.attempt.Try
import graphql.schema.GraphQLOutputType
import java.time.Instant
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet

/**
 * @author smccarron
 * @created 2022-09-01
 */
sealed interface TrackableValue<out V> {

    val targetSourceIndexPath: GQLOperationPath

    val contextualParameters: ImmutableMap<GQLOperationPath, JsonNode>

    val graphQLOutputType: GraphQLOutputType

    fun isPlanned(): Boolean {
        return fold({ true }, { false }, { false })
    }

    fun isCalculated(): Boolean {
        return fold({ false }, { true }, { false })
    }

    fun isTracked(): Boolean {
        return fold({ false }, { false }, { true })
    }

    fun <R> fold(
        planned: (PlannedValue<@UnsafeVariance V>) -> R,
        calculated: (CalculatedValue<@UnsafeVariance V>) -> R,
        tracked: (TrackedValue<@UnsafeVariance V>) -> R
    ): R

    /**
     * Base builder type with methods applicable to all subtypes of [TrackableValue] building and
     * updates
     *
     * Note: The type variable is recursive so subtype builders may return instances of themselves
     * and still preserve the fluent interface of the builder (=> method chaining) type setup
     */
    interface Builder<B : Builder<B>> {

        fun targetSourceIndexPath(targetSourceIndexPath: GQLOperationPath): B

        /** Replaces all current contextual_parameters with this map */
        fun contextualParameters(contextualParameters: ImmutableMap<GQLOperationPath, JsonNode>): B

        fun addContextualParameter(parameterPath: GQLOperationPath, parameterValue: JsonNode): B

        fun addContextualParameter(parameterPathValuePair: Pair<GQLOperationPath, JsonNode>): B

        fun removeContextualParameter(parameterPath: GQLOperationPath): B

        fun clearContextualParameters(): B

        /** Adds all contextual_parameters to the existing map */
        fun addContextualParameters(contextualParameters: Map<GQLOperationPath, JsonNode>): B

        fun graphQLOutputType(graphQLOutputType: GraphQLOutputType): B
    }

    interface PlannedValue<V> : TrackableValue<V> {

        /**
         * @return a new [PlannedValue] instance with the updates if the changes made are valid else
         *   the current instance
         */
        fun update(transformer: Builder.() -> Builder): PlannedValue<V>

        /**
         * @return [CalculatedValue] if both required parameters provided else the current
         *   [PlannedValue]
         */
        fun transitionToCalculated(
            mapper: CalculatedValue.Builder<V>.() -> CalculatedValue.Builder<V>
        ): TrackableValue<V>

        /**
         * @return [TrackedValue] if both required parameters provided else the current
         *   [PlannedValue]
         */
        fun transitionToTracked(
            mapper: TrackedValue.Builder<V>.() -> TrackedValue.Builder<V>
        ): TrackableValue<V>

        override fun <R> fold(
            planned: (PlannedValue<V>) -> R,
            calculated: (CalculatedValue<V>) -> R,
            tracked: (TrackedValue<V>) -> R
        ): R {
            return planned(this)
        }

        interface Builder : TrackableValue.Builder<Builder> {

            fun <V> buildForInstanceOf(): Try<PlannedValue<V>>
        }
    }

    interface CalculatedValue<V> : TrackableValue<V> {

        val calculatedValue: V

        val calculatedTimestamp: Instant

        /**
         * @return a new [CalculatedValue] instance with the updates if the changes made are valid
         *   else the current instance
         */
        fun update(transformer: Builder<V>.() -> Builder<V>): CalculatedValue<V>

        /**
         * @return [TrackedValue] if both required parameters provided else the current
         *   [CalculatedValue]
         */
        fun transitionToTracked(
            mapper: TrackedValue.Builder<V>.() -> TrackedValue.Builder<V>
        ): TrackableValue<V>

        override fun <R> fold(
            planned: (PlannedValue<V>) -> R,
            calculated: (CalculatedValue<V>) -> R,
            tracked: (TrackedValue<V>) -> R
        ): R {
            return calculated(this)
        }

        interface Builder<V> : TrackableValue.Builder<Builder<V>> {

            fun calculatedValue(calculatedValue: V): Builder<V>

            fun calculatedTimestamp(calculatedTimestamp: Instant): Builder<V>

            fun build(): Try<CalculatedValue<V>>
        }
    }

    interface TrackedValue<V> : TrackableValue<V> {

        val canonicalPath: Option<GQLOperationPath>

        val referencePaths: ImmutableSet<GQLOperationPath>

        val trackedValue: V

        val valueAtTimestamp: Instant

        /**
         * @return a new [TrackedValue] instance with the updates if the changes made are valid else
         *   the current instance
         */
        fun update(transformer: Builder<V>.() -> Builder<V>): TrackedValue<V>

        override fun <R> fold(
            planned: (PlannedValue<V>) -> R,
            calculated: (CalculatedValue<V>) -> R,
            tracked: (TrackedValue<V>) -> R
        ): R {
            return tracked(this)
        }

        interface Builder<V> : TrackableValue.Builder<Builder<V>> {

            fun canonicalPath(canonicalPath: GQLOperationPath): Builder<V>

            /** Replaces all current reference_paths with this set */
            fun referencePaths(referencePaths: ImmutableSet<GQLOperationPath>): Builder<V>

            fun addReferencePath(referencePath: GQLOperationPath): Builder<V>

            fun removeReferencePath(referencePath: GQLOperationPath): Builder<V>

            fun clearReferencePaths(): Builder<V>

            /** Adds all reference_paths to the existing set */
            fun addReferencePaths(referencePaths: Iterable<GQLOperationPath>): Builder<V>

            fun trackedValue(trackedValue: V): Builder<V>

            fun valueAtTimestamp(valueAtTimestamp: Instant): Builder<V>

            fun build(): Try<TrackedValue<V>>
        }
    }
}

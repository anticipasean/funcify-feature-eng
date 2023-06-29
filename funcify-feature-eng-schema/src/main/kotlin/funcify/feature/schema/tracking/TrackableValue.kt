package funcify.feature.schema.tracking

import arrow.core.Option
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.attempt.Try
import graphql.schema.GraphQLOutputType
import java.time.Instant
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet

/**
 *
 * @author smccarron
 * @created 2022-09-01
 */
sealed interface TrackableValue<out V> {

    val targetSourceIndexPath: SchematicPath

    val contextualParameters: ImmutableMap<SchematicPath, JsonNode>

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

        fun targetSourceIndexPath(targetSourceIndexPath: SchematicPath): B

        /** Replaces all current contextual_parameters with this map */
        fun contextualParameters(contextualParameters: ImmutableMap<SchematicPath, JsonNode>): B

        fun addContextualParameter(parameterPath: SchematicPath, parameterValue: JsonNode): B

        fun addContextualParameter(parameterPathValuePair: Pair<SchematicPath, JsonNode>): B

        fun removeContextualParameter(parameterPath: SchematicPath): B

        fun clearContextualParameters(): B

        /** Adds all contextual_parameters to the existing map */
        fun addContextualParameters(contextualParameters: Map<SchematicPath, JsonNode>): B

        fun graphQLOutputType(graphQLOutputType: GraphQLOutputType): B
    }

    interface PlannedValue<V> : TrackableValue<V> {

        /**
         * @return a new [PlannedValue] instance with the updates if the changes made are valid else
         * the current instance
         */
        fun <B1, B2> update(
            transformer: Builder<B1>.() -> Builder<B2>
        ): PlannedValue<V> where B1 : Builder<B1>, B2 : Builder<B2>

        /**
         * @return [CalculatedValue] if both required parameters provided else the current
         * [PlannedValue]
         */
        fun <B1, B2> transitionToCalculated(
            mapper: CalculatedValue.Builder<B1, V>.() -> CalculatedValue.Builder<B2, V>
        ): TrackableValue<V> where
        B1 : CalculatedValue.Builder<B1, V>,
        B2 : CalculatedValue.Builder<B2, V>

        /**
         * @return [TrackedValue] if both required parameters provided else the current
         * [PlannedValue]
         */
        fun <B1, B2> transitionToTracked(
            mapper: TrackedValue.Builder<B1, V>.() -> TrackedValue.Builder<B2, V>
        ): TrackableValue<V> where
        B1 : TrackedValue.Builder<B1, V>,
        B2 : TrackedValue.Builder<B2, V>

        override fun <R> fold(
            planned: (PlannedValue<V>) -> R,
            calculated: (CalculatedValue<V>) -> R,
            tracked: (TrackedValue<V>) -> R
        ): R {
            return planned(this)
        }

        interface Builder<B : Builder<B>> : TrackableValue.Builder<B> {

            fun <V> buildForInstanceOf(): Try<PlannedValue<V>>
        }
    }

    interface CalculatedValue<V> : TrackableValue<V> {

        val calculatedValue: V

        val calculatedTimestamp: Instant

        /**
         * @return a new [CalculatedValue] instance with the updates if the changes made are valid
         * else the current instance
         */
        fun <B1, B2> update(
            transformer: Builder<B1, V>.() -> Builder<B2, V>
        ): CalculatedValue<V> where
        B1 : Builder<B1, V>,
        B2 : Builder<B2, V>

        /**
         * @return [TrackedValue] if both required parameters provided else the current
         * [CalculatedValue]
         */
        fun <B1, B2> transitionToTracked(
            mapper: TrackedValue.Builder<B1, V>.() -> TrackedValue.Builder<B2, V>
        ): TrackableValue<V> where
        B1 : TrackedValue.Builder<B1, V>,
        B2 : TrackedValue.Builder<B2, V>

        override fun <R> fold(
            planned: (PlannedValue<V>) -> R,
            calculated: (CalculatedValue<V>) -> R,
            tracked: (TrackedValue<V>) -> R
        ): R {
            return calculated(this)
        }

        interface Builder<B : Builder<B, V>, V> : TrackableValue.Builder<B> {

            fun calculatedValue(calculatedValue: V): B

            fun calculatedTimestamp(calculatedTimestamp: Instant): B

            fun build(): Try<CalculatedValue<V>>
        }
    }

    interface TrackedValue<V> : TrackableValue<V> {

        val canonicalPath: Option<SchematicPath>

        val referencePaths: ImmutableSet<SchematicPath>

        val trackedValue: V

        val valueAtTimestamp: Instant

        /**
         * @return a new [TrackedValue] instance with the updates if the changes made are valid else
         * the current instance
         */
        fun <B1, B2> update(
            transformer: Builder<B1, V>.() -> Builder<B2, V>
        ): TrackedValue<V> where B1 : Builder<B1, V>, B2 : Builder<B2, V>

        override fun <R> fold(
            planned: (PlannedValue<V>) -> R,
            calculated: (CalculatedValue<V>) -> R,
            tracked: (TrackedValue<V>) -> R
        ): R {
            return tracked(this)
        }

        interface Builder<B : Builder<B, V>, V> : TrackableValue.Builder<B> {

            fun canonicalPath(canonicalPath: SchematicPath): B

            /** Replaces all current reference_paths with this set */
            fun referencePaths(referencePaths: ImmutableSet<SchematicPath>): B

            fun addReferencePath(referencePath: SchematicPath): B

            fun removeReferencePath(referencePath: SchematicPath): B

            fun clearReferencePaths(): B

            /** Adds all reference_paths to the existing set */
            fun addReferencePaths(referencePaths: Iterable<SchematicPath>): B

            fun trackedValue(trackedValue: V): B

            fun valueAtTimestamp(valueAtTimestamp: Instant): B

            fun build(): Try<TrackedValue<V>>
        }
    }
}

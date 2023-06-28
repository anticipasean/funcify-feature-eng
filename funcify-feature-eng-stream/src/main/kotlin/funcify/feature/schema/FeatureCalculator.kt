package funcify.feature.schema

import graphql.language.FieldDefinition

/**
 * @author smccarron
 * @created 2023-06-26
 */
interface FeatureCalculator {

    val sdlFieldDefinition: FieldDefinition

    // fun calculateFeature(plannedValue: TrackableValue.PlannedValue<V>): Mono<TrackableValue<V>>

}

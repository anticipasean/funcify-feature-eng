package funcify.feature.schema.datasource

/**
 * Represents a scalar value that may be passed to a given data_source in order to obtain a value or
 * values for a particular [funcify.feature.schema.datasource.SourceIndex]
 *
 * @author smccarron
 * @created 2022-06-18
 */
interface ParameterAttribute<SI : SourceIndex<SI>> : ParameterIndex<SI> {}

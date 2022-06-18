package funcify.feature.schema.datasource

import funcify.feature.naming.ConventionalName
import funcify.feature.schema.path.SchematicPath

/**
 * Represents a value type or set of value types that can or must be passed to the given
 * [DataSource] in order to obtain instances of the parent source index
 *
 * @author smccarron
 * @created 2022-06-18
 */
interface ParameterIndex {

    val dataSourceType: DataSourceType

    val name: ConventionalName

    /**
     * The path for a parameter must contain at least one argument or directive. Otherwise, it
     * violates schema constraints
     */
    val parameterPath: SchematicPath
}

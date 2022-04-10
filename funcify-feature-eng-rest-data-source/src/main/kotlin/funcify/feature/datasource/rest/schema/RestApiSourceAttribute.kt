package funcify.feature.datasource.rest.schema

import funcify.feature.schema.datasource.SourceAttribute


/**
 *
 * @author smccarron
 * @created 1/30/22
 */
interface RestApiSourceAttribute : SourceAttribute {

    fun getJsonPathWithinParentType(): String?

    fun isScalar(): Boolean

    fun isContainerType(): Boolean

}
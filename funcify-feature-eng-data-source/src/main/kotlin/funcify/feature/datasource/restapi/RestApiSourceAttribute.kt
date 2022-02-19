package funcify.feature.datasource.restapi

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
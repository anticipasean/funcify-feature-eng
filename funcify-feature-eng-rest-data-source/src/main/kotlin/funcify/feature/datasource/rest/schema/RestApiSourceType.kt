package funcify.feature.datasource.rest.schema

import funcify.feature.schema.datasource.SourceContainerType
import kotlin.reflect.KClass


/**
 *
 * @author smccarron
 * @created 1/30/22
 */
interface RestApiSourceType : SourceContainerType<RestApiSourceAttribute> {

    fun getAPIObjectType(): KClass<*>

}
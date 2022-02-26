package funcify.feature.datasource.rest.data

import funcify.feature.datasource.rest.RestApiService


/**
 *
 * @author smccarron
 * @created 2/16/22
 */
data class DefaultRestApiService(override val hostName: String,
                                 override val port: UInt,
                                 override val serviceContextPath: String) : RestApiService {

}

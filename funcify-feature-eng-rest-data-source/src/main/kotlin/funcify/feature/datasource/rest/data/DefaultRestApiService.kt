package funcify.feature.datasource.rest.data

import funcify.feature.datasource.rest.RestApiService
import funcify.feature.type.PositiveInt


/**
 *
 * @author smccarron
 * @created 2/16/22
 */
data class DefaultRestApiService(override val hostName: String,
                                 override val port: PositiveInt,
                                 override val serviceContextPath: String) : RestApiService {

}

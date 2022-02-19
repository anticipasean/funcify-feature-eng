package funcify.feature.datasource.rest

import funcify.feature.type.PositiveInt


/**
 *
 * @author smccarron
 * @created 2/16/22
 */
interface RestApiService {

    val hostName: String

    val port: PositiveInt

    val serviceContextPath: String

}
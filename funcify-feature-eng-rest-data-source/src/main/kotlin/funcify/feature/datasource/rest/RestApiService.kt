package funcify.feature.datasource.rest


/**
 *
 * @author smccarron
 * @created 2/16/22
 */
interface RestApiService {

    val hostName: String

    val port: UInt

    val serviceContextPath: String

}
package funcify.feature.error

/**
 *
 * @author smccarron
 * @created 2022-11-10
 */
interface ServiceErrorFactory {

    fun builder(): ServiceError.Builder

}

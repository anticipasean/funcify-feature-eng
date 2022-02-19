package funcify.feature.datasource.rest

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import org.springframework.http.HttpMethod
import kotlin.reflect.KClass


/**
 *
 * @author smccarron
 * @created 2/16/22
 */
interface RestApiDataSource {

    val httpMethod: HttpMethod

    val restApiService: RestApiService

    val sourceSpecificPathComponents: ImmutableList<String>

    val expectedInputTypesByQueryParameterName: ImmutableMap<String, KClass<*>>

}
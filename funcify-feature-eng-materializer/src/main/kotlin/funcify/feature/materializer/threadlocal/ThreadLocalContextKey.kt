package funcify.feature.materializer.threadlocal

import arrow.core.Option
import arrow.core.toOption
import kotlin.reflect.KClass
import org.springframework.core.ParameterizedTypeReference
import org.springframework.core.ResolvableType

/**
 *
 * @author smccarron
 * @created 2022-07-14
 */
interface ThreadLocalContextKey<T : Any> {

    companion object {

        inline fun <reified T : Any> of(keyName: String): ThreadLocalContextKey<T> {
            return of(keyName, object : ParameterizedTypeReference<T>() {})
        }

        fun <T : Any> of(
            keyName: String,
            parameterizedTypeReference: ParameterizedTypeReference<T>
        ): ThreadLocalContextKey<T> {
            return ThreadLocalContextKeyFactory.ParameterizedTypeReferenceContextKey<T>(
                keyName,
                parameterizedTypeReference
            )
        }

        fun <T : Any> of(keyName: String, valueKClass: KClass<T>): ThreadLocalContextKey<T> {
            return ThreadLocalContextKeyFactory.KClassReferenceContextKey<T>(keyName, valueKClass)
        }

        fun <T : Any> of(keyName: String, valueJavaClass: Class<T>): ThreadLocalContextKey<T> {
            return ThreadLocalContextKeyFactory.JavaClassReferenceContextKey(
                keyName,
                valueJavaClass
            )
        }
    }

    val name: String

    val valueResolvableType: ResolvableType

    fun filterValue(value: Any?): Option<T> {
        return value
            .toOption()
            .filter { valueAsAny -> valueResolvableType.isInstance(valueAsAny) }
            .mapNotNull { valueAsAny ->
                @Suppress("UNCHECKED_CAST") //
                valueAsAny as? T
            }
    }
}

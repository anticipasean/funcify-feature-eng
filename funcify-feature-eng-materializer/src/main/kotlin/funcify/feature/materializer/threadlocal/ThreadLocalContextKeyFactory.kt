package funcify.feature.materializer.threadlocal

import kotlin.reflect.KClass
import org.springframework.core.ParameterizedTypeReference
import org.springframework.core.ResolvableType

internal object ThreadLocalContextKeyFactory {

    internal data class ParameterizedTypeReferenceContextKey<T : Any>(
        override val name: String,
        val parameterizedTypeReference: ParameterizedTypeReference<T>
    ) : ThreadLocalContextKey<T> {

        override val valueResolvableType: ResolvableType by lazy {
            ResolvableType.forType(parameterizedTypeReference)
        }
    }

    internal data class KClassReferenceContextKey<T : Any>(
        override val name: String,
        val valueKClass: KClass<T>
    ) : ThreadLocalContextKey<T> {

        override val valueResolvableType: ResolvableType by lazy {
            ResolvableType.forType(valueKClass.java)
        }
    }

    internal data class JavaClassReferenceContextKey<T : Any>(
        override val name: String,
        val valueJavaClass: Class<T>
    ) : ThreadLocalContextKey<T> {

        override val valueResolvableType: ResolvableType by lazy {
            ResolvableType.forType(valueJavaClass)
        }
    }
}

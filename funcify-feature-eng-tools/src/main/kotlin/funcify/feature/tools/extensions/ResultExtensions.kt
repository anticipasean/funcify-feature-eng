package funcify.feature.tools.extensions

import reactor.core.publisher.Mono

object ResultExtensions {

    inline fun <reified S> Result<S>?.toMono(): Mono<out S> {
        return when {
            this == null -> {
                Mono.empty<S>()
            }
            this.isSuccess -> {
                Mono.justOrEmpty<S>(this.getOrNull())
            }
            else -> {
                Mono.error<S>(this.exceptionOrNull()!!)
            }
        }
    }
}

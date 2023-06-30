package funcify.feature.tools.extensions

import reactor.core.publisher.Mono

object ResultExtensions {

    inline fun <reified S> Result<S>?.toMono(): Mono<S> {
        return when {
            this == null -> {
                Mono.empty<S>()
            }
            this.isSuccess -> {
                Mono.justOrEmpty(this.getOrNull())
            }
            else -> {
                Mono.error<S>(this.exceptionOrNull()!!)
            }
        }
    }
}

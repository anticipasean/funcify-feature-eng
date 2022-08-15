package funcify.feature.tools.extensions

import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.container.deferred.Deferred

object DeferredExtensions {

    fun <S> Try<S>.toDeferred(): Deferred<S> {
        return this.fold(Deferred.Companion::completed, Deferred.Companion::failed)
    }

}

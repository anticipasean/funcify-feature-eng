package funcify.feature.tools.container.deferred

import arrow.core.getOrElse
import arrow.core.toOption
import funcify.feature.tools.container.async.KFuture
import java.time.Duration
import java.time.temporal.ChronoUnit
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

internal class DeferredTest {

    @Test
    fun kFutureDeferredTest() {
        val startTimeMs: Long = System.currentTimeMillis()
        val duration =
            Deferred.fromKFuture(
                    KFuture.defer { Thread.sleep(100).run { System.currentTimeMillis() } }
                )
                .map { t: Long -> t - startTimeMs }
                .blockForFirst()
        /*duration.ifSuccess { t -> println("elapsed(ms): $t") }*/
        Assertions.assertTrue { duration.orElse(0L) > 99 }
    }

    @Test
    fun zipFluxMonoTest() {
        val deferredZippedWithDeferredResult =
            Deferred.fromFlux(
                    Flux.fromIterable(0..100).delayElements(Duration.of(5, ChronoUnit.MILLIS))
                )
                .zip(Deferred.fromMono(Mono.just(200)), { r, m -> r to m })
                .blockForAll()
        val unwrappedFluxMonoZipResult =
            Flux.fromIterable(0..100)
                .delayElements(Duration.of(5, ChronoUnit.MILLIS))
                .zipWith(Mono.just(200), { r, m -> r to m })
                .collectList()
                .block()
                .toOption()
        val deferredZippedWithMonoResult =
            Deferred.fromFlux(
                    Flux.fromIterable(0..100).delayElements(Duration.of(5, ChronoUnit.MILLIS))
                )
                .zip(Mono.just(200), { r, m -> r to m })
                .blockForAll()
        Assertions.assertTrue { deferredZippedWithDeferredResult.isSuccess() }
        Assertions.assertTrue { deferredZippedWithMonoResult.isSuccess() }
        Assertions.assertEquals(
            1,
            deferredZippedWithDeferredResult.getSuccess().map { l -> l.size }.getOrElse { -1 }
        )
        Assertions.assertEquals(
            1,
            deferredZippedWithMonoResult.getSuccess().map { l -> l.size }.getOrElse { -1 }
        )
        Assertions.assertEquals(1, unwrappedFluxMonoZipResult.map { l -> l.size }.getOrElse { -1 })
        Assertions.assertEquals(
            0 to 200,
            deferredZippedWithDeferredResult.getSuccess().getOrElse { listOf() }.first()
        )
        Assertions.assertEquals(
            0 to 200,
            deferredZippedWithMonoResult.getSuccess().getOrElse { listOf() }.first()
        )
    }
}

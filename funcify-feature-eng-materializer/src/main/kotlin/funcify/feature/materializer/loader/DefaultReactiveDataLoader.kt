package funcify.feature.materializer.loader

import arrow.core.getOrNone
import arrow.core.orElse
import funcify.feature.error.ServiceError
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.SequenceExtensions.flatMapOptions
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentSet
import org.slf4j.Logger
import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import reactor.core.publisher.Sinks.EmitResult
import reactor.util.concurrent.Queues
import java.util.concurrent.atomic.AtomicBoolean

/**
 * @author smccarron
 * @created 2023-07-28
 */
internal data class DefaultReactiveDataLoader<K, V>(
    private val reactiveBatchDataLoader: ReactiveBatchDataLoader<K, V>,
    private val outputKeys: PersistentList<K> = persistentListOf(),
    private val argumentKeys: PersistentList<K> = persistentListOf(),
    private val submittedArgumentValues: PersistentMap<K, V> = persistentMapOf(),
    private val argumentValuesToBePublished: PersistentMap<K, Mono<out V>> = persistentMapOf(),
    private val sink: Sinks.Many<ImmutableMap<K, V>> = Sinks.many().replay().latest(),
    private val dispatchedFlagHolder: AtomicBoolean = AtomicBoolean(false)
) : ReactiveDataLoader<K, V> {

    companion object {
        private val logger: Logger = loggerFor<DefaultReactiveDataLoader<*, *>>()
    }

    override fun addArgument(key: K, value: V): ReactiveDataLoader<K, V> {
        TODO("Not yet implemented")
    }

    override fun addArguments(arguments: Map<K, V>): ReactiveDataLoader<K, V> {
        TODO("Not yet implemented")
    }

    override fun addArgumentPublisher(key: K, value: Mono<out V>): ReactiveDataLoader<K, V> {
        TODO("Not yet implemented")
    }

    override fun addArgumentPublishers(arguments: Map<K, Mono<out V>>): ReactiveDataLoader<K, V> {
        TODO("Not yet implemented")
    }

    override fun removeArgument(key: K): ReactiveDataLoader<K, V> {
        TODO("Not yet implemented")
    }

    override fun loadDataForKey(key: K): Pair<ReactiveDataLoader<K, V>, Mono<out V>> {
        return when {
            dispatchedFlagHolder.get() -> {
                this to Mono.empty<V>()
            }
            else -> {
                this.copy(outputKeys = outputKeys.add(key)) to
                    sink
                        .asFlux()
                        .next()
                        .mapNotNull<V> { m: ImmutableMap<K, V> -> m[key] }
                        .doOnNext { v: V -> logger.debug("select_output_key: [ key: {} ]", key) }
            }
        }
    }

    override fun loadDataForKeys(
        keys: Set<K>,
    ): Pair<ReactiveDataLoader<K, V>, Mono<out ImmutableMap<K, V>>> {
        TODO("Not yet implemented")
    }

    override fun putDataForKey(key: K, value: V): ReactiveDataLoader<K, V> {
        TODO("Not yet implemented")
    }

    override fun dispatch(): Disposable {
        logger.debug(
            "dispatch: [ argument_keys: {}, output_keys: {}, dispatched: {} ]",
            argumentKeys.asSequence().joinToString(),
            outputKeys.asSequence().joinToString(),
            dispatchedFlagHolder.get()
        )
        return when {
            dispatchedFlagHolder.get() -> {
                Mono.empty<Unit>().subscribe()
            }
            else -> {
                dispatchedFlagHolder.compareAndSet(false, true)
                val argumentsPublisher: Mono<out PersistentMap<K, V>> =
                    Flux.mergeSequentialDelayError(
                            argumentKeys
                                .asSequence()
                                .map { k: K ->
                                    submittedArgumentValues
                                        .getOrNone(k)
                                        .map { v: V -> Mono.just(v) }
                                        .orElse { argumentValuesToBePublished.getOrNone(k) }
                                        .map { vPub: Mono<out V> -> vPub.map { v: V -> k to v } }
                                }
                                .flatMapOptions()
                                .asIterable(),
                            Queues.SMALL_BUFFER_SIZE,
                            Queues.XS_BUFFER_SIZE
                        )
                        .reduce(persistentMapOf<K, V>()) { pm: PersistentMap<K, V>, (k: K, v: V) ->
                            pm.put(k, v)
                        }
                        .cache()
                argumentsPublisher
                    .flatMap { arguments: ImmutableMap<K, V> ->
                        reactiveBatchDataLoader
                            .load(arguments, outputKeys.toPersistentSet())
                            .flatMap { outputVector: ImmutableMap<K, V> ->
                                try {
                                    when (
                                        val nextResult: EmitResult = sink.tryEmitNext(outputVector)
                                    ) {
                                        EmitResult.OK -> {
                                            when (
                                                val completeResult: EmitResult =
                                                    sink.tryEmitComplete()
                                            ) {
                                                EmitResult.OK -> {
                                                    Mono.just(Unit)
                                                }
                                                else -> {
                                                    throw ServiceError.of(
                                                        "load: [ step: emit_complete ][ status: failed ][ reason: %s ]",
                                                        completeResult
                                                    )
                                                }
                                            }
                                        }
                                        else -> {
                                            throw ServiceError.of(
                                                "load: [ step: emit_next ][ status: failed ][ reason: %s ]",
                                                nextResult
                                            )
                                        }
                                    }
                                } catch (t: Throwable) {
                                    when (t) {
                                        is ServiceError -> {
                                            Mono.error<Unit>(t)
                                        }
                                        else -> {
                                            Mono.error<Unit> {
                                                ServiceError.builder()
                                                    .message("load: [ status: failed ]")
                                                    .cause(t)
                                                    .build()
                                            }
                                        }
                                    }
                                }
                            }
                    }
                    .cache()
                    .subscribe()
            }
        }
    }
}

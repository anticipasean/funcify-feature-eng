package funcify.feature.tools.container.attempt

import arrow.typeclasses.Monoid

/**
 *
 * @author smccarron
 * @created 2/7/22
 */
object TryMonoidFactory {

    internal class HomogeneousSuccessTypeTryMonoid<S>(
        private val initial: Try<S>,
        private val combiner: (S, S) -> S
    ) : Monoid<Try<S>> {
        override fun empty(): Try<S> {
            return initial
        }

        override fun Try<S>.combine(b: Try<S>): Try<S> {
            return this.zip(b) { s1: S, s2: S -> combiner.invoke(s1, s2) }
        }
    }

    internal class HeterogeneousSuccessTypeTryMonoid<S, R>(
        private val initial: Try<S>,
        private val mapper: (S) -> R,
        private val combiner: (R, R) -> R
    ) : Monoid<Try<R>> {
        override fun empty(): Try<R> {
            return initial.map(mapper)
        }

        override fun Try<R>.combine(b: Try<R>): Try<R> {
            return this.zip(b) { r1: R, r2: R -> combiner.invoke(r1, r2) }
        }
    }
}

package funcify.feature.tools.extensions

import java.util.AbstractMap.SimpleImmutableEntry

object PairExtensions {

    fun <A, B> Pair<A, B>.swap(): Pair<B, A> {
        return this.second to this.first
    }

    fun <K, V> Pair<K, V>.toEntry(): Map.Entry<K, V> {
        return SimpleImmutableEntry(this.first, this.second)
    }

    inline fun <A, B, C, D> Pair<A, B>.bimap(
        crossinline f1: (A) -> C,
        crossinline f2: (B) -> D
    ): Pair<C, D> {
        return f1.invoke(this.first) to f2.invoke(this.second)
    }

    inline fun <A, B, C, D> Pair<A, B>.flatMap(crossinline f: (A, B) -> Pair<C, D>): Pair<C, D> {
        return f.invoke(this.first, this.second)
    }

    inline fun <A, B, C, D, E, F> Pair<A, B>.zip(
        other: Pair<C, D>,
        crossinline f1: (A, C) -> E,
        crossinline f2: (B, D) -> F
    ): Pair<E, F> {
        return f1.invoke(this.first, other.first) to f2.invoke(this.second, other.second)
    }

    inline fun <A, B, C> Pair<A, B>.fold(crossinline f: (A, B) -> C): C {
        return f.invoke(this.first, this.second)
    }

    inline fun <A, B, C> A.unfold(crossinline f1: (A) -> B, crossinline f2: (A) -> C): Pair<B, C> {
        return f1.invoke(this) to f2.invoke(this)
    }

    inline fun <A, B> Pair<A, B>.bothCondition(
        crossinline f1: (A) -> Boolean,
        crossinline f2: (B) -> Boolean
    ): Boolean {
        return f1.invoke(this.first) && f2.invoke(this.second)
    }

    inline fun <A, B> Pair<A, B>.eitherCondition(
        crossinline f1: (A) -> Boolean,
        crossinline f2: (B) -> Boolean
    ): Boolean {
        return f1.invoke(this.first) || f2.invoke(this.second)
    }
}

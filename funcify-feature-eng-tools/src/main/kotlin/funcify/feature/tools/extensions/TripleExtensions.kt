package funcify.feature.tools.extensions

object TripleExtensions {

    fun <A, B, C> Triple<A, B, C>.swapFirstAndSecond(): Triple<B, A, C> {
        return Triple(this.second, this.first, this.third)
    }

    fun <A, B, C> Triple<A, B, C>.swapSecondAndThird(): Triple<A, C, B> {
        return Triple(this.first, this.third, this.second)
    }

    fun <A, B, C> Triple<A, B, C>.swapFirstAndThird(): Triple<C, B, A> {
        return Triple(this.third, this.second, this.first)
    }

    inline fun <A, B, C, D, E, F> Triple<A, B, C>.trimap(
        crossinline f1: (A) -> D,
        crossinline f2: (B) -> E,
        crossinline f3: (C) -> F
    ): Triple<D, E, F> {
        return Triple(f1.invoke(this.first), f2.invoke(this.second), f3.invoke(this.third))
    }

    inline fun <A, B, C, D> Triple<A, B, C>.mapFirst(crossinline f: (A) -> D): Triple<D, B, C> {
        return Triple(f.invoke(this.first), this.second, this.third)
    }

    inline fun <A, B, C, D> Triple<A, B, C>.mapSecond(crossinline f: (B) -> D): Triple<A, D, C> {
        return Triple(this.first, f.invoke(this.second), this.third)
    }

    inline fun <A, B, C, D> Triple<A, B, C>.mapThird(crossinline f: (C) -> D): Triple<A, B, D> {
        return Triple(this.first, this.second, f.invoke(this.third))
    }

    inline fun <A, B, C, D, E, F> Triple<A, B, C>.flatMap(
        crossinline f: (A, B, C) -> Triple<D, E, F>
    ): Triple<D, E, F> {
        return f.invoke(this.first, this.second, this.third)
    }

    inline fun <A, B, C, D, E, F, G, H, I> Triple<A, B, C>.zip(
        other: Triple<D, E, F>,
        crossinline f1: (A, D) -> G,
        crossinline f2: (B, E) -> H,
        crossinline f3: (C, F) -> I,
    ): Triple<G, H, I> {
        return Triple(
            f1.invoke(this.first, other.first),
            f2.invoke(this.second, other.second),
            f3.invoke(this.third, other.third)
        )
    }

    inline fun <A, B, C, D> Triple<A, B, C>.fold(crossinline f: (A, B, C) -> D): D {
        return f.invoke(this.first, this.second, this.third)
    }

    inline fun <A, B, C, D> A.unfold(
        crossinline f1: (A) -> B,
        crossinline f2: (A) -> C,
        crossinline f3: (A) -> D
    ): Triple<B, C, D> {
        return Triple(f1.invoke(this), f2.invoke(this), f3.invoke(this))
    }
}

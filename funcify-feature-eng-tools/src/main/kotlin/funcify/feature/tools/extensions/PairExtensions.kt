package funcify.feature.tools.extensions

object PairExtensions {

    inline fun <A, B, C, D> Pair<A, B>.bimap(
        crossinline f1: (A) -> C,
        crossinline f2: (B) -> D
    ): Pair<C, D> {
        return f1.invoke(this.first) to f2.invoke(this.second)
    }

    inline fun <A, B, C> Pair<A, B>.fold(crossinline f: (A, B) -> C): C {
        return f.invoke(this.first, this.second)
    }

    inline fun <A, B, C> A.unfold(crossinline f1: (A) -> B, crossinline f2: (A) -> C): Pair<B, C> {
        return f1.invoke(this) to f2.invoke(this)
    }
}

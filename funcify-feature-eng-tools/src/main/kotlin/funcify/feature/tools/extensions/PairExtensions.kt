package funcify.feature.tools.extensions

object PairExtensions {

    fun <A, B, C, D> Pair<A, B>.bimap(f1: (A) -> C, f2: (B) -> D): Pair<C, D> {
        return f1.invoke(this.first) to f2.invoke(this.second)
    }

    fun <A, B, C> Pair<A, B>.fold(f: (A, B) -> C): C {
        return f.invoke(this.first, this.second)
    }

    fun <A, B, C> A.unfold(f1: (A) -> B, f2: (A) -> C): Pair<B, C> {
        return f1.invoke(this) to f2.invoke(this)
    }
}

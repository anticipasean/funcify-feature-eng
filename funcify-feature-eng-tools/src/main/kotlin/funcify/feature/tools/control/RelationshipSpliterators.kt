package funcify.feature.tools.control

import java.util.stream.Stream

object RelationshipSpliterators {

    fun <T> recursiveParentChildSpliterator(
        rootParent: T,
        recursiveChildrenTraverser: (T) -> Stream<out T>
    ): ParentChildPairRelationshipSpliterator<T, T> {
        return ParentChildPairRecursiveSpliterator<T>(
            rootValue = rootParent,
            traversalFunction = recursiveChildrenTraverser
        )
    }
}

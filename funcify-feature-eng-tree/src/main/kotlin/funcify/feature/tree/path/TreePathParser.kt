package funcify.feature.tree.path

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import java.net.URI

internal object TreePathParser : (String) -> Either<IllegalArgumentException, TreePath> {

    override fun invoke(treePathAsString: String): Either<IllegalArgumentException, TreePath> {
        try {
            val uri: URI = URI.create(treePathAsString)
            return if (TreePath.TREE_PATH_SCHEME != uri.scheme) {
                return IllegalArgumentException(
                        "scheme of uri does not match expected scheme [ expected: %s, actual: %s ]".format(
                            TreePath.TREE_PATH_SCHEME,
                            uri.scheme
                        )
                    )
                    .left()
            } else {
                TreePath.of(appendPathSegmentsToBuilder(uri.path)).right()
            }
        } catch (t: Throwable) {
            return IllegalArgumentException(
                    "unable to create tree_path from string: [ type: %s, message: %s ]".format(
                        t::class.simpleName,
                        t.message
                    )
                )
                .left()
        }
    }

    private fun appendPathSegmentsToBuilder(
        path: String,
    ): (TreePath.Builder) -> TreePath.Builder {
        return { builder: TreePath.Builder ->
            path
                .splitToSequence('/')
                .flatMap { s: String ->
                    when {
                        s.isNotBlank() && s[0] >= '0' && s[0] <= '9' -> {
                            try {
                                when (val i: Int? = s.toIntOrNull()) {
                                    null -> {
                                        sequenceOf(s.right())
                                    }
                                    else -> {
                                        sequenceOf(i.left())
                                    }
                                }
                            } catch (e: Exception) {
                                sequenceOf(s.right())
                            }
                        }
                        s.isNotBlank() -> {
                            sequenceOf(s.right())
                        }
                        else -> {
                            emptySequence()
                        }
                    }
                }
                .fold(builder) { bldr: TreePath.Builder, indexOrName: Either<Int, String> ->
                    when (indexOrName) {
                        is Either.Left<Int> -> {
                            bldr.appendPathSegment(indexOrName.value)
                        }
                        is Either.Right<String> -> {
                            bldr.appendPathSegment(indexOrName.value)
                        }
                    }
                }
        }
    }
}

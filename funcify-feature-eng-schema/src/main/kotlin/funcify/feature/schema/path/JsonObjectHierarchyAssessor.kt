package funcify.feature.schema.path

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.tools.control.TraversalFunctions

internal object JsonObjectHierarchyAssessor {

    internal enum class RelationshipType {
        IDENTITY,
        PARENT_CHILD,
        CHILD_PARENT,
        SIBLING_SIBLING,
        ANCESTOR_DESCENDENT,
        DESCENDENT_ANCESTOR,
        NOT_RELATED
    }

    internal data class JsonObjTraversalContext(
        val currentPair1: Pair<String, JsonNode>,
        val currentPair2: Pair<String, JsonNode>,
        val map1Level: Int = 0,
        val map2Level: Int = 0
    )

    fun findRelationshipTypeBetweenTwoJsonObjectMaps(
        map1: Map<String, JsonNode>,
        map2: Map<String, JsonNode>
    ): RelationshipType {
        // val contextPrintStatementFunction: (JsonObjTraversalContext) -> String = { context ->
        //    mapOf<String, Any>(
        //            "pair1" to context.currentPair1,
        //            "pair2" to context.currentPair2,
        //            "mapLevel1" to context.map1Level,
        //            "mapLevel2" to context.map2Level
        //        )
        //        .asSequence()
        //        .joinToString(",\n".padEnd(13, ' '), "{ ", " }", transform = { (k, v) -> "$k: $v"
        // })
        // }
        val traversalFunction:
            (JsonObjTraversalContext) -> Sequence<
                    Either<JsonObjTraversalContext, JsonObjTraversalContext>> =
            { context: JsonObjTraversalContext ->
                // val contextPrintStatement = contextPrintStatementFunction.invoke(context)
                // println("context: ${contextPrintStatement}")
                // println("".padStart(150, '_'))
                if (context.currentPair1.first == context.currentPair2.first) {
                    when (context.currentPair1.second.size()) {
                        0 -> {
                            when (context.currentPair2.second.size()) {
                                0 -> {
                                    sequenceOf(context.right())
                                }
                                else -> {
                                    context.currentPair2.second.fields().asSequence().map { (k, v)
                                        ->
                                        context
                                            .copy(
                                                currentPair2 = k to v,
                                                map2Level = context.map2Level + 1
                                            )
                                            .left()
                                    }
                                }
                            }
                        }
                        else -> {
                            when (context.currentPair2.second.size()) {
                                0 -> {
                                    context.currentPair1.second.fields().asSequence().map { (k, v)
                                        ->
                                        context
                                            .copy(
                                                currentPair1 = (k to v),
                                                map1Level = context.map1Level + 1
                                            )
                                            .left()
                                    }
                                }
                                else -> {
                                    when (
                                        context.currentPair1.second
                                            .size()
                                            .compareTo(context.currentPair2.second.size())
                                    ) {
                                        0 -> {
                                            context.currentPair1.second
                                                .fields()
                                                .asSequence()
                                                .zip(
                                                    context.currentPair2.second
                                                        .fields()
                                                        .asSequence()
                                                )
                                                .map { (nextEntry1, nextEntry2) ->
                                                    context
                                                        .copy(
                                                            currentPair1 = nextEntry1.toPair(),
                                                            currentPair2 = nextEntry2.toPair(),
                                                            map1Level = context.map1Level + 1,
                                                            map2Level = context.map2Level + 1
                                                        )
                                                        .left()
                                                }
                                        }
                                        else -> {
                                            sequenceOf(context.right())
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    when {
                        context.map1Level == context.map2Level -> {
                            sequenceOf(context.right())
                        }
                        context.map1Level < context.map2Level -> {
                            if (context.currentPair2.second.isEmpty) {
                                sequenceOf(context.right())
                            } else {
                                context.currentPair2.second.fields().asSequence().map { (k, v) ->
                                    context
                                        .copy(
                                            currentPair2 = k to v,
                                            map2Level = context.map2Level + 1
                                        )
                                        .left()
                                }
                            }
                        }
                        else -> {
                            if (context.currentPair1.second.isEmpty) {
                                sequenceOf(context.right())
                            } else {
                                context.currentPair1.second.fields().asSequence().map { (k, v) ->
                                    context
                                        .copy(
                                            currentPair1 = k to v,
                                            map1Level = context.map1Level + 1
                                        )
                                        .left()
                                }
                            }
                        }
                    }
                }
            }
        return when (map1.size.compareTo(map2.size)) {
            0 -> {
                map1
                    .asSequence()
                    .zip(map2.asSequence())
                    .map { (entry1, entry2) ->
                        JsonObjTraversalContext(
                            currentPair1 = entry1.toPair(),
                            currentPair2 = entry2.toPair()
                        )
                    }
                    .flatMap { topLevelPairsContext ->
                        TraversalFunctions.recurseWithSequence(
                            topLevelPairsContext,
                            traversalFunction
                        )
                    }
                    .map { context ->
                        when {
                            context.map1Level == context.map2Level -> {
                                when {
                                    context.currentPair1.first == context.currentPair2.first -> {
                                        when {
                                            context.currentPair1.second ==
                                                context.currentPair2.second -> {
                                                RelationshipType.IDENTITY
                                            }
                                            else -> {
                                                // If for any key, the values do not match for two
                                                // nodes mapping to the same parent, then these
                                                // lineages/tree structures are deemed not related
                                                RelationshipType.NOT_RELATED
                                            }
                                        }
                                    }
                                    else -> {
                                        // If all key-value pairs but one differ at the same level
                                        // for the same parent, then they are deemed siblings
                                        RelationshipType.SIBLING_SIBLING
                                    }
                                }
                            }
                            context.map1Level < context.map2Level -> {
                                when (context.map2Level - context.map1Level) {
                                    1 -> RelationshipType.PARENT_CHILD
                                    else -> RelationshipType.ANCESTOR_DESCENDENT
                                }
                            }
                            else -> {
                                when (context.map1Level - context.map2Level) {
                                    1 -> RelationshipType.CHILD_PARENT
                                    else -> RelationshipType.DESCENDENT_ANCESTOR
                                }
                            }
                        }
                    }
                    .filterNot { relType -> relType == RelationshipType.IDENTITY }
                    .reduceOrNull { acc, next ->
                        when (acc) {
                            next -> next
                            else -> RelationshipType.NOT_RELATED
                        }
                    }
                    ?: RelationshipType.IDENTITY
            }
            else -> {
                if (map1.isEmpty() || map2.isEmpty()) {
                    when {
                        map1.isEmpty() -> RelationshipType.PARENT_CHILD
                        else -> RelationshipType.CHILD_PARENT
                    }
                } else {
                    // If these maps have different sets of top level key value pairs as indicated
                    // by
                    // their different key-value set sizes, they are siblings with
                    // respect to root but only if none of their "common" keys have different
                    // corresponding values
                    map1
                        .asSequence()
                        .zip(map2.asSequence())
                        .map { (entry1, entry2) ->
                            when {
                                entry1.key == entry2.key -> {
                                    when {
                                        entry1.value == entry2.value ->
                                            RelationshipType.SIBLING_SIBLING
                                        else -> RelationshipType.NOT_RELATED
                                    }
                                }
                                else -> {
                                    RelationshipType.SIBLING_SIBLING
                                }
                            }
                        }
                        .firstOrNull { relType -> relType != RelationshipType.SIBLING_SIBLING }
                        ?: RelationshipType.SIBLING_SIBLING
                }
            }
        }
    }
}

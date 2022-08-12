package funcify.feature.datasource.graphql.retrieval

import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentSetValueMap
import funcify.feature.tools.extensions.SequenceExtensions.flatMapOptions
import java.util.*
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

internal object GraphQLQueryPathBasedComposer {

    private data class QueryCompositionContext(
        val stack: LinkedList<QueryCompositionContext> = LinkedList(),
        val path: SchematicPath,
        val level: Int = 0,
        val parameterPaths: PersistentList<SchematicPath> = persistentListOf()
    )

    fun createQueryCompositionFunction(
        graphQLSourcePaths: ImmutableSet<SchematicPath>
    ): (ImmutableMap<SchematicPath, JsonNode>) -> String {
        val sourceAttributeSetsByParentPath:
            PersistentMap<SchematicPath, PersistentSet<SchematicPath>> =
            graphQLSourcePaths
                .asSequence()
                .filter { sp -> sp.arguments.isEmpty() && sp.directives.isEmpty() }
                .map { srcAttrPath -> srcAttrPath.getParentPath().map { pp -> pp to srcAttrPath } }
                .flatMapOptions()
                .sortedBy { (sp, _) -> sp }
                .reducePairsToPersistentSetValueMap()
        val parameterAttributeSetsByParentPath:
            PersistentMap<SchematicPath, PersistentSet<SchematicPath>> =
            graphQLSourcePaths
                .asSequence()
                .filter { paramPath ->
                    paramPath.arguments.isNotEmpty() || paramPath.directives.isNotEmpty()
                }
                .map { paramPath -> paramPath.getParentPath().map { pp -> pp to paramPath } }
                .flatMapOptions()
                .sortedBy { (sp, _) -> sp }
                .reducePairsToPersistentSetValueMap()
        val finalContext: QueryCompositionContext =
            sourceAttributeSetsByParentPath.asSequence().fold(
                QueryCompositionContext(path = SchematicPath.getRootPath())
            ) { context, (parentPath, srcAttrSet) ->
                srcAttrSet
                    .asSequence()
                    .map { srcAttrPath ->
                        parameterAttributeSetsByParentPath[srcAttrPath]
                            .toOption()
                            .fold(
                                { srcAttrPath to persistentSetOf() },
                                { paramAttrSet -> srcAttrPath to paramAttrSet }
                            )
                    }
                    .fold(context) { ctx, (srcAttrPath, paramAttrPathSet) ->
                        // TODO: Continue work here
                        if (!parentPath.isRoot()) {
                            ctx
                        } else {
                            ctx
                        }
                    }
            }
        val queryTemplateString: String? = null
        return { parameterValuesByVertexPath: ImmutableMap<SchematicPath, JsonNode> ->
            queryTemplateString ?: "<NA>"
        }
    }
}

package funcify.feature.materializer.graph.connector

import arrow.core.filterIsInstance
import arrow.core.lastOrNone
import funcify.feature.materializer.graph.RawInputContextShape
import funcify.feature.materializer.graph.RawInputProvided
import funcify.feature.materializer.graph.RequestMaterializationGraphConnector
import funcify.feature.materializer.graph.RequestMaterializationGraphContext
import funcify.feature.schema.path.Field
import funcify.feature.schema.path.GQLOperationPath

/**
 * @author smccarron
 * @created 2023-08-05
 */
interface RawInputBasedGraphConnector<C> : RequestMaterializationGraphConnector<C> where
C : RawInputProvided,
C : RequestMaterializationGraphContext {

    fun containsRawInputKeyForFieldWithName(context: C, name: String): Boolean {
        return when (val rics: RawInputContextShape = context.rawInputContextShape) {
            is RawInputContextShape.Tabular -> {
                rics.columnSet.contains(name) ||
                    context.materializationMetamodel.featureEngineeringModel.attributeAliasRegistry
                        .getSourceVertexPathWithSimilarNameOrAlias(name)
                        .flatMap { gop: GQLOperationPath ->
                            gop.selection
                                .lastOrNone()
                                .filterIsInstance<Field>()
                                .map(Field::fieldName)
                                .filter { fn: String -> rics.columnSet.contains(fn) }
                        }
                        .isDefined()
            }
            is RawInputContextShape.Tree -> {
                rics.fieldNames.contains(name)
            }
        }
    }
}

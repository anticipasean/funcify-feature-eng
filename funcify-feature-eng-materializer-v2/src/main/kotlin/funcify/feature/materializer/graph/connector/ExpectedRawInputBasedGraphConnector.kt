package funcify.feature.materializer.graph.connector

import arrow.core.filterIsInstance
import arrow.core.lastOrNone
import funcify.feature.materializer.graph.input.RawInputContextShape
import funcify.feature.materializer.graph.input.ExpectedRawInputShape
import funcify.feature.materializer.graph.context.RequestMaterializationGraphContext
import funcify.feature.materializer.input.RawInputContext
import funcify.feature.schema.path.operation.FieldSegment
import funcify.feature.schema.path.operation.GQLOperationPath

/**
 * @author smccarron
 * @created 2023-08-05
 */
interface ExpectedRawInputBasedGraphConnector<C> : RequestMaterializationGraphConnector<C> where
C : ExpectedRawInputShape,
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
                                .filterIsInstance<FieldSegment>()
                                .map(FieldSegment::fieldName)
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

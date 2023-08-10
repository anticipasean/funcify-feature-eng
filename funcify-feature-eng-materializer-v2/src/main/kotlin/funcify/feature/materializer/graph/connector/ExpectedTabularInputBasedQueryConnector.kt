package funcify.feature.materializer.graph.connector

import arrow.core.filterIsInstance
import arrow.core.lastOrNone
import funcify.feature.materializer.graph.context.RequestMaterializationGraphContext
import funcify.feature.materializer.graph.input.RawInputContextShape
import funcify.feature.schema.path.operation.FieldSegment
import funcify.feature.schema.path.operation.GQLOperationPath

/**
 * @author smccarron
 * @created 2023-08-10
 */
interface ExpectedTabularInputBasedQueryConnector<C> :
    RequestMaterializationGraphConnector<C> where
C : RequestMaterializationGraphContext,
C : RawInputContextShape.Tabular {

    fun containsRawInputKeyForFieldWithName(context: C, name: String): Boolean {
        return context.columnSet.contains(name) ||
            context.materializationMetamodel.featureEngineeringModel.attributeAliasRegistry
                .getSourceVertexPathWithSimilarNameOrAlias(name)
                .flatMap { gop: GQLOperationPath ->
                    gop.selection
                        .lastOrNone()
                        .filterIsInstance<FieldSegment>()
                        .map(FieldSegment::fieldName)
                        .filter { fn: String -> context.columnSet.contains(fn) }
                }
                .isDefined()
    }
}

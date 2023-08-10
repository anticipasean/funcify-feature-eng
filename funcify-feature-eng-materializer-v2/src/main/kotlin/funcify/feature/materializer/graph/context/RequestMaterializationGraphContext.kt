package funcify.feature.materializer.graph.context

import funcify.feature.graph.DirectedPersistentGraph
import funcify.feature.materializer.graph.MaterializationEdge
import funcify.feature.materializer.graph.input.ExpectedRawInputShape
import funcify.feature.materializer.graph.input.RawInputContextShape
import funcify.feature.materializer.graph.target.StandardQueryTarget
import funcify.feature.materializer.graph.target.TabularQueryTarget
import funcify.feature.materializer.schema.MaterializationMetamodel
import funcify.feature.schema.dataelement.DataElementSource
import funcify.feature.schema.feature.FeatureCalculator
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.transformer.TransformerSource
import graphql.language.Document
import graphql.language.Node
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet

/**
 * @author smccarron
 * @created 2023-07-31
 */
interface RequestMaterializationGraphContext {

    val materializationMetamodel: MaterializationMetamodel

    val variableKeys: ImmutableSet<String>

    val requestGraph: DirectedPersistentGraph<GQLOperationPath, Node<*>, MaterializationEdge>

    val transformerCallableBuildersByPath: ImmutableMap<GQLOperationPath, TransformerSource.Builder>

    val dataElementCallableBuildersByPath: ImmutableMap<GQLOperationPath, DataElementSource.Builder>

    val featureCalculatorCallableBuildersByPath:
        ImmutableMap<GQLOperationPath, FeatureCalculator.Builder>

    interface ExpectedStandardJsonInputStandardQuery :
        RequestMaterializationGraphContext,
        StandardQueryTarget,
        ExpectedRawInputShape.StandardJsonInputShape {

        override val document: Document

        override val standardJsonShape: RawInputContextShape.Tree
    }

    interface ExpectedTabularInputStandardQuery :
        RequestMaterializationGraphContext,
        StandardQueryTarget,
        ExpectedRawInputShape.TabularInputShape {

        override val document: Document

        override val tabularShape: RawInputContextShape.Tabular
    }

    interface StandardQuery : RequestMaterializationGraphContext, StandardQueryTarget {

        override val document: Document
    }

    interface ExpectedStandardJsonInputTabularQuery :
        RequestMaterializationGraphContext,
        ExpectedRawInputShape.StandardJsonInputShape,
        TabularQueryTarget {

        override val outputColumnNames: ImmutableSet<String>

        override val standardJsonShape: RawInputContextShape.Tree
    }

    interface ExpectedTabularInputTabularQuery :
        RequestMaterializationGraphContext,
        ExpectedRawInputShape.TabularInputShape,
        TabularQueryTarget {

        override val outputColumnNames: ImmutableSet<String>

        override val tabularShape: RawInputContextShape.Tabular
    }

    interface TabularQuery : RequestMaterializationGraphContext, TabularQueryTarget {

        override val outputColumnNames: ImmutableSet<String>
    }
}

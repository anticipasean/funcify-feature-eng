package funcify.feature.schema.strategy

import arrow.core.identity
import arrow.core.toOption
import arrow.core.zip
import funcify.feature.schema.datasource.DataElementSource
import funcify.feature.schema.datasource.ParameterAttribute
import funcify.feature.schema.datasource.ParameterContainerType
import funcify.feature.schema.datasource.SourceAttribute
import funcify.feature.schema.datasource.SourceContainerType
import funcify.feature.schema.vertex.ParameterJunctionVertex
import funcify.feature.schema.vertex.ParameterLeafVertex
import funcify.feature.schema.vertex.SourceJunctionVertex
import funcify.feature.schema.vertex.SourceLeafVertex
import funcify.feature.schema.vertex.SourceRootVertex

/**
 *
 * @author smccarron
 * @created 2022-07-18
 */
interface SchematicVertexSourceIndexBasedMappingTemplate<C> :
    SchematicVertexGraphBasedMappingTemplate<C> {

    override fun onSourceRootVertex(sourceRootVertex: SourceRootVertex, context: C): C {
        return sourceRootVertex.compositeContainerType
            .getSourceContainerTypeByDataSource()
            .asSequence()
            .fold(context) { ctx, (dataSourceKey, sourceContainerType) ->
                onSourceIndexOnDataSourceOnSourceRootVertex(
                    dataSourceKey,
                    sourceContainerType,
                    sourceRootVertex,
                    ctx
                )
            }
    }

    override fun onSourceJunctionVertex(sourceJunctionVertex: SourceJunctionVertex, context: C): C {
        return sourceJunctionVertex.compositeContainerType
            .toOption()
            .zip(sourceJunctionVertex.compositeAttribute.toOption()) {
                compositeSourceContainerType,
                compositeSourceAttribute,
                ->
                compositeSourceContainerType
                    .getSourceContainerTypeByDataSource()
                    .zip(compositeSourceAttribute.getSourceAttributeByDataSource())
            }
            .fold(::emptyMap, ::identity)
            .asSequence()
            .fold(context) { ctx, (dataSourceKey, srcContTypeAttrPair) ->
                onSourceIndicesOnDataSourceOnSourceJunctionVertex(
                    dataSourceKey,
                    srcContTypeAttrPair.first,
                    srcContTypeAttrPair.second,
                    sourceJunctionVertex,
                    ctx
                )
            }
    }

    override fun onSourceLeafVertex(sourceLeafVertex: SourceLeafVertex, context: C): C {
        return sourceLeafVertex.compositeAttribute
            .getSourceAttributeByDataSource()
            .asSequence()
            .fold(context) { ctx, (dataSourceKey, sourceAttr) ->
                onSourceIndexOnDataSourceOnSourceLeafVertex(
                    dataSourceKey,
                    sourceAttr,
                    sourceLeafVertex,
                    ctx
                )
            }
    }

    override fun onParameterJunctionVertex(
        parameterJunctionVertex: ParameterJunctionVertex,
        context: C
    ): C {
        return parameterJunctionVertex.compositeParameterContainerType
            .toOption()
            .zip(parameterJunctionVertex.compositeParameterAttribute.toOption()) { pct, pa ->
                pct.getParameterContainerTypeByDataSource()
                    .zip(pa.getParameterAttributesByDataSource())
            }
            .fold(::emptyMap, ::identity)
            .asSequence()
            .fold(context) { ctx, (dataSourceKey, paramContAttrPair) ->
                onSourceIndicesOnDataSourceOnParameterJunctionVertex(
                    dataSourceKey,
                    paramContAttrPair.first,
                    paramContAttrPair.second,
                    parameterJunctionVertex,
                    ctx
                )
            }
    }

    override fun onParameterLeafVertex(parameterLeafVertex: ParameterLeafVertex, context: C): C {
        return parameterLeafVertex.compositeParameterAttribute
            .getParameterAttributesByDataSource()
            .asSequence()
            .fold(context) { ctx, (dataSourceKey, paramAttr) ->
                onSourceIndexOnDataSourceOnParameterLeafVertex(
                    dataSourceKey,
                    paramAttr,
                    parameterLeafVertex,
                    ctx
                )
            }
    }

    fun onSourceIndexOnDataSourceOnSourceRootVertex(
        dataSourceKey: DataElementSource.Key<*>,
        sourceContainerType: SourceContainerType<*, *>,
        sourceRootVertex: SourceRootVertex,
        context: C
    ): C

    fun onSourceIndicesOnDataSourceOnSourceJunctionVertex(
        dataSourceKey: DataElementSource.Key<*>,
        sourceContainerType: SourceContainerType<*, *>,
        sourceAttribute: SourceAttribute<*>,
        sourceJunctionVertex: SourceJunctionVertex,
        context: C
    ): C

    fun onSourceIndexOnDataSourceOnSourceLeafVertex(
        dataSourceKey: DataElementSource.Key<*>,
        sourceAttribute: SourceAttribute<*>,
        sourceLeafVertex: SourceLeafVertex,
        context: C
    ): C

    fun onSourceIndicesOnDataSourceOnParameterJunctionVertex(
        dataSourceKey: DataElementSource.Key<*>,
        parameterContainerType: ParameterContainerType<*, *>,
        parameterAttribute: ParameterAttribute<*>,
        parameterJunctionVertex: ParameterJunctionVertex,
        context: C
    ): C

    fun onSourceIndexOnDataSourceOnParameterLeafVertex(
        dataSourceKey: DataElementSource.Key<*>,
        parameterAttribute: ParameterAttribute<*>,
        parameterLeafVertex: ParameterLeafVertex,
        context: C
    ): C
}

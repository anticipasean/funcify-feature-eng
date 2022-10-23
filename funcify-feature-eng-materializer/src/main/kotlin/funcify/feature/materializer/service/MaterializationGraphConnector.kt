package funcify.feature.materializer.service

import arrow.core.Either
import arrow.core.Option
import arrow.core.none
import funcify.feature.materializer.error.MaterializerErrorResponse
import funcify.feature.materializer.error.MaterializerException
import funcify.feature.materializer.context.graph.MaterializationGraphContext
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.vertex.ParameterAttributeVertex
import funcify.feature.schema.vertex.ParameterJunctionVertex
import funcify.feature.schema.vertex.ParameterLeafVertex
import funcify.feature.schema.vertex.SourceAttributeVertex
import funcify.feature.schema.vertex.SourceJunctionVertex
import funcify.feature.schema.vertex.SourceLeafVertex
import funcify.feature.schema.vertex.SourceRootVertex
import graphql.language.Argument
import graphql.language.Field

/**
 *
 * @author smccarron
 * @created 2022-10-09
 */
interface MaterializationGraphConnector {

    fun connectSchematicVertex(
        fieldOrArgument: Option<Either<Field, Argument>> = none(),
        vertex: SchematicVertex,
        context: MaterializationGraphContext
    ): MaterializationGraphContext {
        return when (vertex) {
            is SourceRootVertex -> {
                connectSourceRootVertex(vertex, context)
            }
            is SourceJunctionVertex -> {
                connectSourceJunctionOrLeafVertex(
                    fieldOrArgument.mapNotNull { fOrA -> fOrA.swap().orNull() },
                    vertex,
                    context
                )
            }
            is SourceLeafVertex -> {
                connectSourceJunctionOrLeafVertex(
                    fieldOrArgument.mapNotNull { fOrA -> fOrA.swap().orNull() },
                    vertex,
                    context
                )
            }
            is ParameterJunctionVertex -> {
                connectParameterJunctionOrLeafVertex(
                    fieldOrArgument.mapNotNull { fOrA -> fOrA.orNull() },
                    vertex,
                    context
                )
            }
            is ParameterLeafVertex -> {
                connectParameterJunctionOrLeafVertex(
                    fieldOrArgument.mapNotNull { fOrA -> fOrA.orNull() },
                    vertex,
                    context
                )
            }
            else -> {
                throw MaterializerException(
                    MaterializerErrorResponse.UNEXPECTED_ERROR,
                    "unhandled vertex type: [ type: ${vertex::class.simpleName} ]"
                )
            }
        }
    }

    fun connectSourceRootVertex(
        vertex: SourceRootVertex,
        context: MaterializationGraphContext
    ): MaterializationGraphContext

    fun <V : SourceAttributeVertex> connectSourceJunctionOrLeafVertex(
        field: Option<Field> = none(),
        vertex: V,
        context: MaterializationGraphContext
    ): MaterializationGraphContext

    fun <V : ParameterAttributeVertex> connectParameterJunctionOrLeafVertex(
        argument: Option<Argument> = none(),
        vertex: V,
        context: MaterializationGraphContext
    ): MaterializationGraphContext


}

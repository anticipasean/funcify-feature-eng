package funcify.feature.materializer.schema.path

import arrow.core.Option
import arrow.core.filterIsInstance
import arrow.core.orElse
import arrow.core.some
import arrow.core.toOption
import funcify.feature.materializer.schema.MaterializationMetamodel
import funcify.feature.schema.MetamodelGraph
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.vertex.SourceAttributeVertex
import funcify.feature.schema.vertex.SourceContainerTypeVertex
import graphql.schema.FieldCoordinates
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

internal object SchematicPathFieldCoordinatesMatcher :
    (MaterializationMetamodel, SchematicPath) -> Option<FieldCoordinates> {

    private val schematicPathFieldCoordinatesMemoizer:
        (MaterializationMetamodel, SchematicPath) -> Option<FieldCoordinates> by lazy {
        val cache: ConcurrentMap<Pair<Instant, SchematicPath>, FieldCoordinates> =
            ConcurrentHashMap()
        ({ mm: MaterializationMetamodel, path: SchematicPath ->
            cache
                .computeIfAbsent(mm.created to path, schematicPathFieldCoordinatesCalculator(mm))
                .toOption()
        })
    }

    override fun invoke(
        materializationMetamodel: MaterializationMetamodel,
        schematicPath: SchematicPath
    ): Option<FieldCoordinates> {
        return schematicPathFieldCoordinatesMemoizer(materializationMetamodel, schematicPath)
    }

    private fun schematicPathFieldCoordinatesCalculator(
        materializationMetamodel: MaterializationMetamodel
    ): (Pair<Instant, SchematicPath>) -> FieldCoordinates? {
        return { (materializationMetamodelCreated: Instant, path: SchematicPath) ->
            val mmg: MetamodelGraph = materializationMetamodel.metamodelGraph
            path
                .toOption()
                .filter { p -> p.arguments.isNotEmpty() || p.directives.isNotEmpty() }
                .map { p -> p.transform { clearArguments().clearDirectives() } }
                .orElse { path.some() }
                .flatMap { p ->
                    p.getParentPath()
                        .flatMap { parentSourceVertexPath ->
                            mmg.pathBasedGraph
                                .getVertex(parentSourceVertexPath)
                                .filterIsInstance<SourceContainerTypeVertex>()
                        }
                        .zip(
                            mmg.pathBasedGraph
                                .getVertex(p)
                                .filterIsInstance<SourceAttributeVertex>()
                        )
                }
                .mapNotNull { (sct, sa) ->
                    FieldCoordinates.coordinates(
                        sct.compositeContainerType.conventionalName.qualifiedForm,
                        sa.compositeAttribute.conventionalName.qualifiedForm
                    )
                }
                .orNull()
        }
    }
}

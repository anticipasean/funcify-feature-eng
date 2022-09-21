package funcify.feature.materializer.schema.path

import arrow.core.Option
import arrow.core.filterIsInstance
import arrow.core.orElse
import arrow.core.some
import arrow.core.toOption
import funcify.feature.schema.MetamodelGraph
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.vertex.SourceAttributeVertex
import funcify.feature.schema.vertex.SourceContainerTypeVertex
import graphql.schema.FieldCoordinates
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

internal object SchematicPathFieldCoordinatesMatcher :
    (SchematicPath, MetamodelGraph) -> Option<FieldCoordinates> {

    private val schematicPathFieldCoordinatesMemoizer:
        (SchematicPath, MetamodelGraph) -> Option<FieldCoordinates> by lazy {
        val cache: ConcurrentMap<Pair<SchematicPath, MetamodelGraph>, FieldCoordinates> =
            ConcurrentHashMap()
        ({ path: SchematicPath, mmg: MetamodelGraph ->
            cache.computeIfAbsent(path to mmg, schematicPathFieldCoordinatesCalculator()).toOption()
        })
    }

    override fun invoke(
        path: SchematicPath,
        metamodelGraph: MetamodelGraph
    ): Option<FieldCoordinates> {
        return schematicPathFieldCoordinatesMemoizer(path, metamodelGraph)
    }

    private fun schematicPathFieldCoordinatesCalculator():
        (Pair<SchematicPath, MetamodelGraph>) -> FieldCoordinates? {
        return { (path: SchematicPath, mmg: MetamodelGraph) ->
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

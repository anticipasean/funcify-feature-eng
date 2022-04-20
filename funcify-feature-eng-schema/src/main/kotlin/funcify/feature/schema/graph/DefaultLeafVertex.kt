package funcify.feature.schema.graph

import funcify.feature.schema.index.CompositeAttribute
import funcify.feature.schema.path.SchematicPath

internal data class DefaultLeafVertex(
    override val path: SchematicPath,
    override val compositeAttribute: CompositeAttribute
) : LeafVertex

package funcify.feature.schema.vertex

import funcify.feature.schema.index.CompositeAttribute
import funcify.feature.schema.path.SchematicPath

internal data class DefaultLeafVertex(
    override val path: SchematicPath,
    override val compositeAttribute: CompositeAttribute
) : LeafVertex

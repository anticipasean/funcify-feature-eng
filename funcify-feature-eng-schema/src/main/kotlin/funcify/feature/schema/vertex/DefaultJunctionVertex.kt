package funcify.feature.schema.vertex

import funcify.feature.schema.index.CompositeAttribute
import funcify.feature.schema.index.CompositeContainerType
import funcify.feature.schema.path.SchematicPath

internal data class DefaultJunctionVertex(
    override val path: SchematicPath,
    override val compositeContainerType: CompositeContainerType,
    override val compositeAttribute: CompositeAttribute
) : JunctionVertex

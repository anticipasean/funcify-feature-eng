package funcify.feature.schema.vertex

import funcify.feature.schema.index.CompositeContainerType
import funcify.feature.schema.path.SchematicPath

internal data class DefaultRootVertex(
    override val path: SchematicPath,
    override val compositeContainerType: CompositeContainerType
) : RootVertex

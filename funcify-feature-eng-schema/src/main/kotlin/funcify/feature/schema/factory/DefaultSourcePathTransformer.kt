package funcify.feature.schema.factory

import funcify.feature.schema.datasource.SourcePathTransformer
import funcify.feature.schema.path.SchematicPath

class DefaultSourcePathTransformer(private val rootPathSegment: String) : SourcePathTransformer {

    override fun transformSourcePathToSchematicPath(sourcePath: SchematicPath): SchematicPath {
        return sourcePath.prependPathSegment(rootPathSegment)
    }

}

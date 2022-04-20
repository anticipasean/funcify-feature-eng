package funcify.feature.schema.datasource

import funcify.feature.schema.path.SchematicPath

interface SourcePathTransformer {

    fun transformSourcePathToSchematicPath(sourcePath: SchematicPath): SchematicPath

}


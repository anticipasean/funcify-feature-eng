package funcify.feature.datasource.rest.schema

import funcify.feature.schema.path.SchematicPath
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet

/**
 *
 * @author smccarron
 * @created 2022-07-12
 */
internal class DefaultSwaggerRestApiSourceMetamodel(
    override val sourceIndicesByPath:
        PersistentMap<SchematicPath, PersistentSet<RestApiSourceIndex>>
) : SwaggerRestApiSourceMetamodel {}

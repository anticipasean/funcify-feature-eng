package funcify.feature.materializer.document

import java.time.Instant

internal data class PreparsedDocumentEntryCacheKey(
    val materializationMetamodelCreated: Instant,
    val rawGraphQLQueryText: String
)

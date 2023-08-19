package funcify.feature.materializer.graph

import arrow.core.Option
import graphql.language.Document
import java.time.Instant
import kotlinx.collections.immutable.ImmutableSet

data class RequestMaterializationGraphCacheKey(
    val materializationMetamodelCreated: Instant,
    val variableKeys: ImmutableSet<String>,
    val rawInputContextKeys: ImmutableSet<String>,
    val operationName: Option<String>,
    val standardQueryDocument: Option<Document>,
    val tabularQueryOutputColumns: Option<ImmutableSet<String>>,
)

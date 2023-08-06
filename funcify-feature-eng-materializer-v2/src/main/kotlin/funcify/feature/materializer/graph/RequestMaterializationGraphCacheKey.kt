package funcify.feature.materializer.graph

import arrow.core.Option
import funcify.feature.materializer.graph.input.RawInputContextShape
import graphql.language.Document
import kotlinx.collections.immutable.ImmutableSet

data class RequestMaterializationGraphCacheKey(
    val variableKeys: ImmutableSet<String>,
    val standardQueryDocument: Option<Document>,
    val tabularQueryOutputColumns: Option<ImmutableSet<String>>,
    val rawInputContextShape: Option<RawInputContextShape>
)

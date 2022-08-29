package funcify.feature.datasource.rest.retrieval

import arrow.core.Either
import funcify.feature.datasource.rest.RestApiDataSource
import funcify.feature.datasource.rest.schema.SwaggerParameterAttribute
import funcify.feature.datasource.rest.schema.SwaggerSourceAttribute
import funcify.feature.json.JsonMapper
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.vertex.ParameterJunctionVertex
import funcify.feature.schema.vertex.ParameterLeafVertex
import funcify.feature.schema.vertex.SourceJunctionVertex
import funcify.feature.schema.vertex.SourceLeafVertex
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet

interface SwaggerRestApiJsonResponsePostProcessingContext {

    val jsonMapper: JsonMapper

    val dataSource: RestApiDataSource

    val parameterVertices: ImmutableSet<Either<ParameterJunctionVertex, ParameterLeafVertex>>

    val sourceVertices: ImmutableSet<Either<SourceJunctionVertex, SourceLeafVertex>>

    val parentPathVertexPair:
        Pair<SchematicPath, Either<SourceJunctionVertex, SourceLeafVertex>>

    val parentVertexPathToSwaggerSourceAttribute: Pair<SchematicPath, SwaggerSourceAttribute>

    val swaggerSourceAttributesByVertexPath: ImmutableMap<SchematicPath, SwaggerSourceAttribute>

    val swaggerParameterAttributesByVertexPath: ImmutableMap<SchematicPath, SwaggerParameterAttribute>

    val sourceVertexPathBySourceIndexPath: ImmutableMap<SchematicPath, SchematicPath>
}

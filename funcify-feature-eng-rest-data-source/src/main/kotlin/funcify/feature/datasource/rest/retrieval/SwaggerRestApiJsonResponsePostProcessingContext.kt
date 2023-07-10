package funcify.feature.datasource.rest.retrieval

import funcify.feature.datasource.rest.RestApiDataElementSource
import funcify.feature.tools.json.JsonMapper

interface SwaggerRestApiJsonResponsePostProcessingContext {

    val jsonMapper: JsonMapper

    val dataSource: RestApiDataElementSource
    /*
    val parameterVertices: ImmutableSet<Either<ParameterJunctionVertex, ParameterLeafVertex>>

    val sourceVertices: ImmutableSet<Either<SourceJunctionVertex, SourceLeafVertex>>

    val parentPathVertexPair:
        Pair<SchematicPath, Either<SourceJunctionVertex, SourceLeafVertex>>

    val parentVertexPathToSwaggerSourceAttribute: Pair<SchematicPath, SwaggerSourceAttribute>

    val swaggerSourceAttributesByVertexPath: ImmutableMap<SchematicPath, SwaggerSourceAttribute>

    val swaggerParameterAttributesByVertexPath: ImmutableMap<SchematicPath, SwaggerParameterAttribute>

    val sourceVertexPathBySourceIndexPath: ImmutableMap<SchematicPath, SchematicPath>*/
}

package funcify.feature.datasource.rest.swagger

import funcify.feature.datasource.rest.swagger.SwaggerV3ParserSourceIndexContext.Companion.SV3PWT
import io.swagger.v3.oas.models.OpenAPI

/**
 *
 * @author smccarron
 * @created 2022-07-12
 */
interface SwaggerV3ParserSourceIndexFactory :
    SwaggerV3ParserSourceIndexCreationTraversalTemplate<SV3PWT>,
    SwaggerV3ParserSourceIndexContextMutationTemplate<SV3PWT> {


    override fun onOpenAPI(
        openAPIRepresentation: OpenAPI,
        contextContainer: SwaggerSourceIndexContextContainer<SV3PWT>,
    ): SwaggerSourceIndexContextContainer<SV3PWT> {
        return super.onOpenAPI(openAPIRepresentation, contextContainer)
    }

}

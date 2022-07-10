package funcify.feature.datasource.rest.metadata.provider

import com.fasterxml.jackson.databind.ObjectMapper
import funcify.feature.datasource.rest.RestApiService
import funcify.feature.tools.container.deferred.Deferred
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import io.swagger.v3.oas.models.OpenAPI
import org.apache.commons.lang3.StringEscapeUtils
import org.slf4j.Logger
import org.yaml.snakeyaml.external.com.google.gdata.util.common.base.UnicodeEscaper

/**
 *
 * @author smccarron
 * @created 2022-07-09
 */
class DefaultSwaggerRestApiMetadataProvider(private val objectMapper: ObjectMapper) :
    SwaggerRestApiMetadataProvider {

    companion object {
        private val logger: Logger = loggerFor<DefaultSwaggerRestApiMetadataProvider>()
    }

    override fun provideMetadata(service: RestApiService): Deferred<OpenAPI> {
        logger.info(
            """provide_metadata: [ service: 
            |{ name: ${service.serviceName}, 
            |host: ${service.hostName}, 
            |port: ${service.port} } ]
            |""".flattenIntoOneLine()
        )

        TODO("Not yet implemented")
    }
}

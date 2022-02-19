package funcify.feature.configuration

import arrow.core.getOrElse
import arrow.core.toOption
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.persistentMapOf
import org.apache.camel.CamelContext
import org.apache.camel.spring.boot.CamelContextConfiguration
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


/**
 *
 * @author smccarron
 * @created 2/16/22
 */
@Configuration
class ApacheCamelRoutingConfiguration {

    companion object {
        val logger: Logger = LoggerFactory.getLogger(ApacheCamelRoutingConfiguration::class.java)
    }

    @Bean
    fun camelContextConfiguration(): CamelContextConfiguration {
        return FeatureServiceCamelContextConfiguration()
    }


    internal class FeatureServiceCamelContextConfiguration : CamelContextConfiguration {

        override fun beforeApplicationStart(camelContext: CamelContext?) {
            val loggableParamsMap = extractLoggableParametersFromCamelContextIntoMap(camelContext)
            logger.info("before_application_start: [ camel_context: {} ]", loggableParamsMap.toString())
        }

        private fun extractLoggableParametersFromCamelContextIntoMap(camelContext: CamelContext?): PersistentMap<String, String> {
            return camelContext.toOption()
                    .map { ctx -> ctx.componentNames.joinToString(separator = ", ", prefix = "[ ", postfix = " ]") }
                    .zip(camelContext.toOption()
                                 .map { ctx ->
                                     ctx.routes.map { r -> r.id }
                                             .joinToString(separator = ", ", prefix = "[ ", postfix = " ]")
                                 })
                    .map { pair -> persistentHashMapOf(Pair("component_names", pair.first), Pair("routes", pair.second)) }
                    .getOrElse { persistentMapOf() }
        }

        override fun afterApplicationStart(camelContext: CamelContext?) {
            val loggableParamsMap = extractLoggableParametersFromCamelContextIntoMap(camelContext)
            logger.info("after_application_start: [ camel_context: {} ]", loggableParamsMap.toString())
        }


    }

}
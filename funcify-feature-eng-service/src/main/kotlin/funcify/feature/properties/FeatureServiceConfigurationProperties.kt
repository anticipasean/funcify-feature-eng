package funcify.feature.properties

import funcify.feature.tool.validation.ValidTimeZone
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.InitializingBean
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.validation.annotation.Validated
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotEmpty


/**
 *
 * @author smccarron
 * @created 2/16/22
 */
@Validated
@Configuration
@ConfigurationProperties(prefix = "funcify-feature-eng")
data class FeatureServiceConfigurationProperties(

        @get:ValidTimeZone
        @get:NotBlank(message = "No system-default-time-zone property is set")
        var systemDefaultTimeZone: String = "UTC",

        @get:NotEmpty(message = "no rest-data-sources were found in configuration")
        var restDataSources: List<String> = mutableListOf()) : InitializingBean {

    override fun afterPropertiesSet() {
        logger.info("feature_service_configuration_properties.after_properties_set: {}", this.toString())
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(FeatureServiceConfigurationProperties::class.java)
    }

}

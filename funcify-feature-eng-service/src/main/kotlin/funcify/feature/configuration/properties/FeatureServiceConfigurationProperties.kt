package funcify.feature.configuration.properties

import funcify.feature.datasource.graphql.GraphQLApiService
import funcify.feature.datasource.graphql.factory.GraphQLApiServiceFactory
import funcify.feature.naming.StandardNamingConventions
import funcify.feature.tool.validation.ValidTimeZone
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotEmpty
import kotlin.reflect.KClass
import kotlinx.collections.immutable.persistentListOf
import org.slf4j.Logger
import org.springframework.beans.factory.InitializingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.validation.annotation.Validated

/**
 *
 * @author smccarron
 * @created 2/16/22
 */
@Validated
@Configuration
@ConfigurationProperties(prefix = "funcify-feature-eng")
data class FeatureServiceConfigurationProperties
@ConstructorBinding
constructor(
    @get:ValidTimeZone //
    @get:NotBlank(message = "No system-default-time-zone property is set") //
    var systemDefaultTimeZone: String = "UTC", //
    @get:NotEmpty(message = "No graphql-api-data-sources were found") //
    var graphQlApiDataSources: Map<String, GraphQLApiDataSourceProperties> = mutableMapOf(), //
    @get:NotEmpty(message = "No rest-api-data-sources were found") //
    var restApiDataSources: Map<String, RestApiDataSourceProperties> = mutableMapOf() //
) : InitializingBean {

    companion object {
        private val logger: Logger = loggerFor<FeatureServiceConfigurationProperties>()
        private fun KClass<*>.snakeCaseName(): String {
            return StandardNamingConventions.SNAKE_CASE.deriveName(this.simpleName ?: "").toString()
        }

        data class GraphQLApiDataSourceProperties
        @ConstructorBinding
        constructor(val hostName: String, val serviceContextPath: String)
        data class RestApiDataSourceProperties
        @ConstructorBinding
        constructor(val hostName: String, val serviceContextPath: String)
    }

    override fun afterPropertiesSet() {
        val kClassSnakeCaseName = FeatureServiceConfigurationProperties::class.snakeCaseName()
        logger.info("${kClassSnakeCaseName}.after_properties_set: {}", this)
    }

    @ConditionalOnBean(value = [GraphQLApiServiceFactory::class])
    @Bean
    fun graphQLApiServices(
        graphQLApiServiceFactory: GraphQLApiServiceFactory
    ): List<GraphQLApiService> {
        return graphQlApiDataSources
            .entries
            .asSequence()
            .map { entry: Map.Entry<String, GraphQLApiDataSourceProperties> ->
                val graphQLServiceContextPath: String =
                    if (entry.value.serviceContextPath.endsWith("/graphql")) {
                        entry.value.serviceContextPath
                    } else {
                        entry.value.serviceContextPath + "/graphql"
                    }
                graphQLApiServiceFactory
                    .builder()
                    .serviceName(entry.key)
                    .hostName(entry.value.hostName)
                    .serviceContextPath(graphQLServiceContextPath)
                    .build()
            }
            .fold(persistentListOf()) { pl, gqlServ -> pl.add(gqlServ) }
    }
}

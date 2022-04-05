package funcify.feature.json;


import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.jayway.jsonpath.Option
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider
import com.jayway.jsonpath.spi.json.JsonProvider
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider
import com.jayway.jsonpath.spi.mapper.MappingProvider
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder

/**
 * Central location for creation of the Jackson {@link ObjectMapper} to be used throughout the
 * application
 * <p></p>
 * Places where the default {@code new ObjectMapper()} call is used to be limited to tests and wherever else the context demands
 * it so that serialization and deserialization into JSON consistently is applied wherever necessary, implicit (e.g. API calls via
 * {@link org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration}) or explicit
 *
 * @author smccarron
 * @created 2021-07-24
 */
@Configuration(proxyBeanMethods = false)
class JsonObjectMappingConfiguration {

    /**
     * Leverages whatever autoconfiguration and configuration properties that are enabled on SpringBoot, only adding to the
     * autoconfigured object mapper builder any features or modules that might be missing depending on the autoconfiguration
     * setup
     *
     * @return customization function to be called by {@link org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration}
     * if present and the application context calls it
     */
    @Bean
    fun jsonObjectMappingBuilderCustomizer(): Jackson2ObjectMapperBuilderCustomizer {
        return Jackson2ObjectMapperBuilderCustomizer { jacksonObjectMapperBuilder ->
            jacksonObjectMapperBuilder?.let {
                builderCustomizationConsumer().invoke(it)
            }
        }
    }


    /**
     * After spring's jackson auto configuration has run and used the customizer provided, take that
     * builder instance and enhance it.
     *
     * @param jackson2ObjectMapperBuilder
     * @return the jackson json object mapper
     */
    @Primary
    @Bean
    fun objectMapper(jackson2ObjectMapperBuilder: Jackson2ObjectMapperBuilder): ObjectMapper {
        return postBuildObjectMapperEnhancer(jackson2ObjectMapperBuilder.build());
    }

    companion object {


        /**
         * Separate functional piece of customization action to avoid problems with proxy bean methods
         *
         * @return consumer that takes a jackson object mapper builder and customizes it for this application
         */
        @JvmStatic
        private fun builderCustomizationConsumer(): (Jackson2ObjectMapperBuilder) -> Unit =
                { builder ->
                    builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                            .featuresToEnable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                }


        /**
         * If these updates are done as part of the postconfigurer block in the builder customization,
         * the current version of jackson overwrites the existing modules set and does not enable adding them back.
         * So, these enhancements must be kept separate for now
         *
         * @return
         */
        @JvmStatic
        private fun postBuildObjectMapperEnhancer(objectMapper: ObjectMapper): ObjectMapper {
            objectMapper.configOverride(List::class.java)
                    .setSetterInfo(JsonSetter.Value.forValueNulls(Nulls.AS_EMPTY,
                                                                  Nulls.AS_EMPTY))
            objectMapper.configOverride(Map::class.java)
                    .setSetterInfo(JsonSetter.Value.forValueNulls(Nulls.AS_EMPTY,
                                                                  Nulls.AS_EMPTY))
            return objectMapper
        }


        /**
         * For use in tests where the full Spring application context need not be instantiated or
         * is too cumbersome and thus autoconfiguration would not create the
         * {@link Jackson2ObjectMapperBuilder} instance and run the
         * {@link Jackson2ObjectMapperBuilderCustomizer}s on it
         *
         * @return object mapper instance with the provided modules plus the defaults
         */
        @JvmStatic
        fun objectMapper(): ObjectMapper {
            val builder: Jackson2ObjectMapperBuilder = Jackson2ObjectMapperBuilder.json();
            builderCustomizationConsumer().invoke(builder);
            return postBuildObjectMapperEnhancer(builder.build());
        }
    }

    @Primary
    @Bean
    fun jaywayJsonPathFrameworkJsonProvider(objectMapper: ObjectMapper): JsonProvider {
        return JacksonJsonNodeJsonProvider(objectMapper)
    }

    @Primary
    @Bean
    fun jaywayJsonPathFrameworkMappingProvider(objectMapper: ObjectMapper): MappingProvider {
        return JacksonMappingProvider(objectMapper)
    }

    @Primary
    @Bean
    fun jaywayJsonPathFrameworkConfiguration(jaywayJsonPathFrameworkJsonProvider: JsonProvider,
                                             jaywayJsonPathFrameworkMappingProvider: MappingProvider): com.jayway.jsonpath.Configuration {
        val configuration: com.jayway.jsonpath.Configuration = com.jayway.jsonpath.Configuration.builder()
                .jsonProvider(jaywayJsonPathFrameworkJsonProvider)
                .mappingProvider(jaywayJsonPathFrameworkMappingProvider)
                .build();
        com.jayway.jsonpath.Configuration.setDefaults(object : com.jayway.jsonpath.Configuration.Defaults {
            override fun jsonProvider(): JsonProvider {
                return configuration.jsonProvider()
            }

            override fun options(): MutableSet<Option> {
                return configuration.options
            }

            override fun mappingProvider(): MappingProvider {
                return configuration.mappingProvider()
            }

        });
        return configuration;
    }

}

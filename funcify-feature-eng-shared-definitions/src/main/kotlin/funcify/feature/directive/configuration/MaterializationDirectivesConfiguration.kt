package funcify.feature.directive.configuration

import funcify.feature.directive.AliasDirective
import funcify.feature.directive.LastUpdatedDirective
import funcify.feature.directive.MaterializationDirective
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MaterializationDirectivesConfiguration {

    @Bean
    fun supportedMaterializationDirectives(): List<MaterializationDirective> {
        return listOf(AliasDirective, LastUpdatedDirective)
    }


}

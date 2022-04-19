package funcify.feature.materializer.config

import funcify.feature.datasource.graphql.GraphQLApiDataSource
import org.springframework.context.annotation.Configuration

@Configuration
class MaterializerConfiguration {


    fun graphQLApiDataSources(datasources: List<GraphQLApiDataSource>)


}

package funcify.feature.materializer.schema

import arrow.core.continuations.AtomicRef
import funcify.feature.error.ServiceError
import funcify.feature.materializer.model.MaterializationMetamodel
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import org.slf4j.Logger
import reactor.core.publisher.Mono

internal class DefaultMaterializationMetamodelBroker : MaterializationMetamodelBroker {

    companion object {
        private val logger: Logger = loggerFor<DefaultMaterializationMetamodelBroker>()
    }

    private val materializationMetamodelHolder: AtomicRef<MaterializationMetamodel?> = AtomicRef()

    override fun pushNewMaterializationMetamodel(
        materializationMetamodel: MaterializationMetamodel
    ) {
        logger.info(
            """push_new_materialization_metamodel: [ materialization_metamodel: { created: {}, 
            |graphql_schema.query_type.field_definitions.size: {} 
            |} ]"""
                .flatten(),
            materializationMetamodel.created,
            materializationMetamodel.materializationGraphQLSchema.queryType.fieldDefinitions.size
        )
        materializationMetamodelHolder.getAndUpdate { current: MaterializationMetamodel? ->
            if (current == null) {
                materializationMetamodel
            } else if (current.created < materializationMetamodel.created) {
                materializationMetamodel
            } else {
                current
            }
        }
    }

    override fun fetchLatestMaterializationMetamodel(): Mono<MaterializationMetamodel> {
        logger.info("fetch_latest_materialization_schema: []")
        return Mono.defer {
            when (
                val materializationMetamodel: MaterializationMetamodel? =
                    materializationMetamodelHolder.get()
            ) {
                null -> {
                    Mono.error {
                        ServiceError.builder()
                            .message(
                                "no materialization_metamodel has been provided to this broker instance"
                            )
                            .build()
                    }
                }
                else -> {
                    Mono.just(materializationMetamodel)
                }
            }
        }
    }
}

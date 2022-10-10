package funcify.feature.materializer.schema

import arrow.core.continuations.AtomicRef
import funcify.feature.materializer.error.MaterializerErrorResponse
import funcify.feature.materializer.error.MaterializerException
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import org.slf4j.Logger
import reactor.core.publisher.Mono

internal class DefaultMaterializationMetamodelBroker() : MaterializationMetamodelBroker {

    companion object {
        private val logger: Logger = loggerFor<DefaultMaterializationMetamodelBroker>()
    }

    private val schemaAtTimestampHolder: AtomicRef<MaterializationMetamodel?> = AtomicRef()

    override fun pushNewMaterializationMetamodel(
        materializationMetamodel: MaterializationMetamodel
    ) {
        logger.info(
            """push_new_materialization_metamodel: [ materialization_metamodel: { created: {}, 
            |graphql_schema.query_type.field_definitions.size: {} 
            |} ]""".flatten(),
            materializationMetamodel.created,
            materializationMetamodel.materializationGraphQLSchema.queryType.fieldDefinitions.size
        )
        schemaAtTimestampHolder.getAndUpdate { metamodel ->
            if (metamodel == null) {
                materializationMetamodel
            } else if (metamodel.created < materializationMetamodel.created) {
                materializationMetamodel
            } else {
                metamodel
            }
        }
    }

    override fun fetchLatestMaterializationMetamodel(): Mono<MaterializationMetamodel> {
        logger.info("fetch_latest_materialization_schema: []")
        return Mono.fromSupplier { ->
            when (
                val materializationMetamodel: MaterializationMetamodel? =
                    schemaAtTimestampHolder.get()
            ) {
                null -> {
                    throw MaterializerException(
                        MaterializerErrorResponse.GRAPHQL_SCHEMA_CREATION_ERROR,
                        "no materialization_metamodel has been provided to this broker instance"
                    )
                }
                else -> {
                    materializationMetamodel
                }
            }
        }
    }
}

package funcify.feature.materializer.schema

import arrow.core.continuations.AtomicRef
import funcify.feature.materializer.error.MaterializerErrorResponse
import funcify.feature.materializer.error.MaterializerException
import funcify.feature.tools.container.async.KFuture
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import org.slf4j.Logger
import reactor.core.publisher.Mono

internal class DefaultMaterializationMetamodelBroker() : MaterializationMetamodelBroker {

    companion object {
        private val logger: Logger = loggerFor<DefaultMaterializationMetamodelBroker>()
    }

    private val schemaAtTimestampHolder: AtomicRef<Pair<Long, MaterializationMetamodel>?> =
        AtomicRef()

    override fun pushNewMaterializationMetamodel(
        materializationMetamodel: MaterializationMetamodel
    ) {
        logger.info(
            """push_new_materialization_metamodel: [ 
                |materialization_metamodel.graphql_schema.query_type.field_definitions.size: 
                |${materializationMetamodel.materializationGraphQLSchema.queryType.fieldDefinitions.size} 
                |]""".flatten()
        )
        val pushTime = System.currentTimeMillis()
        schemaAtTimestampHolder.getAndUpdate { storedTimestampAndSchema ->
            if (storedTimestampAndSchema == null) {
                pushTime to materializationMetamodel
            } else if (storedTimestampAndSchema.first < pushTime) {
                pushTime to materializationMetamodel
            } else {
                storedTimestampAndSchema
            }
        }
    }

    override fun fetchLatestMaterializationMetamodel(): KFuture<MaterializationMetamodel> {
        logger.info("fetch_latest_materialization_schema: []")
        return KFuture.fromMono(
            Mono.fromSupplier { ->
                when (
                    val materializationMetamodel: MaterializationMetamodel? =
                        schemaAtTimestampHolder.get()?.second
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
        )
    }
}

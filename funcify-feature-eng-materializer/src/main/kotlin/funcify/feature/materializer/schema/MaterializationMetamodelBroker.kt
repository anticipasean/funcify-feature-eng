package funcify.feature.materializer.schema

import reactor.core.publisher.Mono

interface MaterializationMetamodelBroker {

    fun pushNewMaterializationMetamodel(materializationMetamodel: MaterializationMetamodel)

    fun fetchLatestMaterializationMetamodel(): Mono<MaterializationMetamodel>
}

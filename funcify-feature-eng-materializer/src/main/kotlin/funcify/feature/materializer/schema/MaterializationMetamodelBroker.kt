package funcify.feature.materializer.schema

import funcify.feature.materializer.model.MaterializationMetamodel
import reactor.core.publisher.Mono

interface MaterializationMetamodelBroker {

    fun pushNewMaterializationMetamodel(materializationMetamodel: MaterializationMetamodel)

    fun fetchLatestMaterializationMetamodel(): Mono<MaterializationMetamodel>
}

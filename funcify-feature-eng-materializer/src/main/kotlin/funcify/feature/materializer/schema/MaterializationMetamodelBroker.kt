package funcify.feature.materializer.schema

import funcify.feature.tools.container.async.KFuture

interface MaterializationMetamodelBroker {

    fun pushNewMaterializationMetamodel(materializationMetamodel: MaterializationMetamodel)

    fun fetchLatestMaterializationMetamodel(): KFuture<MaterializationMetamodel>

}

package funcify.feature.materializer.schema

import funcify.feature.tools.container.deferred.Deferred

interface MaterializationMetamodelBroker {

    fun pushNewMaterializationMetamodel(materializationMetamodel: MaterializationMetamodel)

    fun fetchLatestMaterializationMetamodel(): Deferred<MaterializationMetamodel>

}

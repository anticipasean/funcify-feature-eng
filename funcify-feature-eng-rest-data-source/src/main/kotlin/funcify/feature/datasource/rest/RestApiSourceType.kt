package funcify.feature.datasource.rest

import funcify.feature.schema.SourceType

object RestApiSourceType : SourceType {
    override val name: String
        get() = "RestApi"
}

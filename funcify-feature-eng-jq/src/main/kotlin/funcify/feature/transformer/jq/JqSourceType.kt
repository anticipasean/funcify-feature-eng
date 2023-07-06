package funcify.feature.transformer.jq

import funcify.feature.schema.SourceType

object JqSourceType: SourceType {

    override val name: String
        get() = "Jq"

}

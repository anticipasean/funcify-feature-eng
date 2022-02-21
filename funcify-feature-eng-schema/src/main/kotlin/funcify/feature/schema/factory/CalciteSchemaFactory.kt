package funcify.feature.schema.factory

import org.apache.calcite.tools.FrameworkConfig
import org.springframework.stereotype.Component


/**
 *
 * @author smccarron
 * @created 2/20/22
 */
@Component
class CalciteSchemaFactory(val frameworkConfig: FrameworkConfig) {

    companion object {
        private const val ADD_METADATA_SCHEMA_FLAG = true
    }


}
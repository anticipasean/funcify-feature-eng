package funcify.feature.materializer.service

import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import org.slf4j.Logger

internal class DefaultMaterializationGraphQLWiringFactory() : MaterializationGraphQLWiringFactory {

    companion object {
        private val logger: Logger = loggerFor<DefaultMaterializationGraphQLWiringFactory>()
    }


}

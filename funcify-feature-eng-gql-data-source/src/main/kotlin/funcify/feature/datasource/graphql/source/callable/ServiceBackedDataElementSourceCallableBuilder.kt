package funcify.feature.datasource.graphql.source.callable

import arrow.core.continuations.eagerEffect
import arrow.core.continuations.ensureNotNull
import arrow.core.identity
import arrow.core.toOption
import funcify.feature.datasource.graphql.ServiceBackedDataElementSource
import funcify.feature.error.ServiceError
import funcify.feature.schema.dataelement.DataElementCallable
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import org.slf4j.Logger

internal class ServiceBackedDataElementSourceCallableBuilder() :
    BaseDataElementCallableBuilder<ServiceBackedDataElementSourceCallableBuilder>() {

    companion object {
        private val logger: Logger = loggerFor<ServiceBackedDataElementSourceCallableBuilder>()
    }

    override fun build(): DataElementCallable {
        if (logger.isDebugEnabled) {
            logger.debug(
                "build: [ domain_field_coordinates: {} ]",
                this.domainSpecifiedDataElementSource?.domainFieldCoordinates
            )
        }
        return eagerEffect<String, ServiceBackedDataElementSourceCallable> {
                ensureNotNull(domainSpecifiedDataElementSource) {
                    "domain_specified_data_element_source has not been provided"
                }
                ensure(
                    domainSpecifiedDataElementSource?.dataElementSource
                        is ServiceBackedDataElementSource
                ) {
                    "domain_specified_data_element_source.data_element_source is not of type %s"
                        .format(ServiceBackedDataElementSource::class.qualifiedName)
                }
                ensure(
                    fieldPathSelections.asSequence().all { p: GQLOperationPath ->
                        p.isDescendentTo(domainSpecifiedDataElementSource!!.domainPath)
                    }
                ) {
                    "not all field_path_selections belong within domain path"
                }
                ServiceBackedDataElementSourceCallable(
                    domainSpecifiedDataElementSource = domainSpecifiedDataElementSource!!,
                    serviceBackedDataElementSource =
                        domainSpecifiedDataElementSource!!.dataElementSource
                            as ServiceBackedDataElementSource,
                    selections = fieldPathSelections.build(),
                    directivePathSelections = directivePathSelections.build(),
                    directivePathSelectionsWithValues = directivePathValueSelections.build(),
                )
            }
            .fold(
                { message: String ->
                    throw ServiceError.of(
                        """unable to create %s 
                        |for graphql_data_element_source [ message: %s ]"""
                            .flatten(),
                        ServiceBackedDataElementSourceCallable::class.simpleName,
                        message
                    )
                },
                ::identity
            )
    }
}

package funcify.feature.datasource.graphql.source.callable

import arrow.core.continuations.eagerEffect
import arrow.core.continuations.ensureNotNull
import arrow.core.filterIsInstance
import arrow.core.identity
import arrow.core.toOption
import funcify.feature.error.ServiceError
import funcify.feature.schema.dataelement.DataElementCallable
import funcify.feature.schema.dataelement.DomainSpecifiedDataElementSource
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import graphql.language.Field
import graphql.language.Selection
import graphql.language.SelectionSet
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLFieldsContainer
import graphql.schema.GraphQLTypeUtil
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
                ensure(fieldSelection != null || fieldPathSelections.isNotEmpty()) {
                    "neither a selected_field nor field_path_selections have been provided"
                }
                ensure(
                    fieldSelection == null ||
                        fieldSelection
                            .toOption()
                            .zip(
                                domainSpecifiedDataElementSource
                                    .toOption()
                                    .map(DomainSpecifiedDataElementSource::domainFieldDefinition)
                            )
                            .filter { (f: Field, gfd: GraphQLFieldDefinition) ->
                                f.name == gfd.name
                            }
                            .flatMap { (f: Field, gfd: GraphQLFieldDefinition) ->
                                f.toOption()
                                    .mapNotNull(Field::getSelectionSet)
                                    .mapNotNull(SelectionSet::getSelections)
                                    .filter(List<Selection<*>>::isNotEmpty)
                                    .and(
                                        GraphQLTypeUtil.unwrapAll(gfd.type)
                                            .toOption()
                                            .filterIsInstance<GraphQLFieldsContainer>()
                                            .map(GraphQLFieldsContainer::getFieldDefinitions)
                                            .filter(List<GraphQLFieldDefinition>::isNotEmpty)
                                    )
                            }
                            .isDefined()
                ) {
                    """selected_field either does not match domain_field_coordinates.field_name 
                        |or has sub-selections 
                        |but does not match a graphql_field_definition with child nodes"""
                        .flatten()
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
                    selections = fieldPathSelections.build(),
                    selectedField = fieldSelection.toOption(),
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

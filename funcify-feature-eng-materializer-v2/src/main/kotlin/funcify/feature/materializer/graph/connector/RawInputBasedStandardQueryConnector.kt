package funcify.feature.materializer.graph.connector

import funcify.feature.materializer.graph.RequestMaterializationGraphConnector
import funcify.feature.materializer.graph.RequestMaterializationGraphContext.RawInputProvidedStandardQuery
import funcify.feature.schema.path.GQLOperationPath
import graphql.language.Argument
import graphql.language.Field
import graphql.language.OperationDefinition

/**
 * @author smccarron
 * @created 2023-08-05
 */
interface RawInputBasedStandardQueryConnector :
    RequestMaterializationGraphConnector<RawInputProvidedStandardQuery> {
    override fun createOperationDefinition(
        context: RawInputProvidedStandardQuery
    ): RawInputProvidedStandardQuery

    override fun connectOperationDefinitionToFieldArgument(
        context: RawInputProvidedStandardQuery,
        opDefPath: GQLOperationPath,
        operationDefinition: OperationDefinition,
        fieldArgPath: GQLOperationPath,
        argument: Argument,
    ): RawInputProvidedStandardQuery

    override fun connectOperationDefinitionToField(
        context: RawInputProvidedStandardQuery,
        opDefPath: GQLOperationPath,
        operationDefinition: OperationDefinition,
        fieldPath: GQLOperationPath,
        field: Field,
    ): RawInputProvidedStandardQuery

    override fun connectFieldToFieldArgument(
        context: RawInputProvidedStandardQuery,
        fieldPath: GQLOperationPath,
        field: Field,
        fieldArgPath: GQLOperationPath,
        argument: Argument,
    ): RawInputProvidedStandardQuery

    override fun connectFieldArgumentToField(
        context: RawInputProvidedStandardQuery,
        fieldArgPath: GQLOperationPath,
        argument: Argument,
        fieldPath: GQLOperationPath,
        field: Field,
    ): RawInputProvidedStandardQuery

    override fun connectFieldToField(
        context: RawInputProvidedStandardQuery,
        fieldPath1: GQLOperationPath,
        field1: Field,
        fieldPath2: GQLOperationPath,
        field2: Field,
    ): RawInputProvidedStandardQuery
}

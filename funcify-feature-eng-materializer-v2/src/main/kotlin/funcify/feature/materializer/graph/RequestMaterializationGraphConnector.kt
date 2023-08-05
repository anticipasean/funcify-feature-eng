package funcify.feature.materializer.graph

import funcify.feature.schema.path.GQLOperationPath
import graphql.language.Argument
import graphql.language.Field
import graphql.language.OperationDefinition

/**
 * @author smccarron
 * @created 2023-08-02
 */
interface RequestMaterializationGraphConnector<C> {

    fun createOperationDefinition(context: C): C

    fun connectOperationDefinitionToFieldArgument(
        context: C,
        opDefPath: GQLOperationPath,
        operationDefinition: OperationDefinition,
        fieldArgPath: GQLOperationPath,
        argument: Argument
    ): C

    fun connectOperationDefinitionToField(
        context: C,
        opDefPath: GQLOperationPath,
        operationDefinition: OperationDefinition,
        fieldPath: GQLOperationPath,
        field: Field
    ): C

    fun connectFieldToFieldArgument(
        context: C,
        fieldPath: GQLOperationPath,
        field: Field,
        fieldArgPath: GQLOperationPath,
        argument: Argument
    ): C

    fun connectFieldArgumentToField(
        context: C,
        fieldArgPath: GQLOperationPath,
        argument: Argument,
        fieldPath: GQLOperationPath,
        field: Field
    ): C

    fun connectFieldToField(
        context: C,
        fieldPath1: GQLOperationPath,
        field1: Field,
        fieldPath2: GQLOperationPath,
        field2: Field
    ): C
}

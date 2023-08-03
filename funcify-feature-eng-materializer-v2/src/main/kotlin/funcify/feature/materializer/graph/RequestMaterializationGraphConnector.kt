package funcify.feature.materializer.graph

/**
 *
 * @author smccarron
 * @created 2023-08-02
 */
interface RequestMaterializationGraphConnector {

    fun connectOperationDefinitionToFieldArgument()

    fun connectOperationDefinitionToField()

    fun connectFieldToFieldArgument()

    fun connectFieldArgumentToField()

    fun connectFieldToField()

}

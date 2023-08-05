package funcify.feature.materializer.graph

/**
 *
 * @author smccarron
 * @created 2023-08-02
 */
interface RequestMaterializationGraphConnector<C> {

    fun connectOperationDefinitionToFieldArgument(context: C): C

    fun connectOperationDefinitionToField(context: C): C

    fun connectFieldToFieldArgument(context: C): C

    fun connectFieldArgumentToField(context: C): C

    fun connectFieldToField(context: C): C

}

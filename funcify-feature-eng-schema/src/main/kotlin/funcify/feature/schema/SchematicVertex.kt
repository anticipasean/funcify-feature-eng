package funcify.feature.schema


/**
 * Represents a node within a feature function graph
 * @author smccarron
 * @created 1/30/22
 */
interface SchematicVertex {

    val path: SchematicPath

    val compositeIndex: CompositeIndex

}
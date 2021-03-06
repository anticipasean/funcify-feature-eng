package funcify.feature.schema.datasource


/**
 * Type instances used in associating a given source index: container and/or attribute type
 * with a type of data source
 * Could implement an enum class type or some other implementation as long as it is a singleton
 * within the context of multiple data source types
 * @author smccarron
 * @created 1/30/22
 */
interface DataSourceType {

    val name: String

}
package funcify.feature.datasource.reldb


/**
 *
 * @author smccarron
 * @created 2/1/22
 */
interface RelTableIdentifier {

    val catalogName: String

    val schemaName: String

    val tableName: String

    fun mkString(): String {
        return when {
            catalogName.isNotEmpty() && schemaName.isNotEmpty() && tableName.isNotEmpty() -> {
                "${catalogName}.${schemaName}.${tableName}"
            }
            schemaName.isNotEmpty() && tableName.isNotEmpty() -> "${schemaName}.${tableName}"
            tableName.isNotEmpty() -> tableName
            else -> ""
        }
    }

}
package funcify.feature.datasource.sqldb


/**
 *
 * @author smccarron
 * @created 2/1/22
 */
interface SqlDatabaseTable {

    val databaseCatalogName: String

    val schemaName: String

    val tableName: String

    fun mkString(): String {
        return when {
            databaseCatalogName.isNotEmpty() && schemaName.isNotEmpty() && tableName.isNotEmpty() -> {
                "${databaseCatalogName}.${schemaName}.${tableName}"
            }
            schemaName.isNotEmpty() && tableName.isNotEmpty() -> "${schemaName}.${tableName}"
            tableName.isNotEmpty() -> tableName
            else -> ""
        }
    }

}
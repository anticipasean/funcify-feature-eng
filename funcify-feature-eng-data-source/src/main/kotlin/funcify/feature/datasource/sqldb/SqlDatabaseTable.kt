package funcify.feature.datasource.sqldb


/**
 *
 * @author smccarron
 * @created 2/1/22
 */
interface SqlDatabaseTable {

    fun getDatabaseCatalogName(): String

    fun getSchemaName(): String

    fun getTableName(): String

    fun mkString(): String {
        return when {
            getDatabaseCatalogName().isNotEmpty() && getSchemaName().isNotEmpty() && getTableName().isNotEmpty() -> {
                "${getDatabaseCatalogName()}.${getSchemaName()}.${getTableName()}"
            }
            getSchemaName().isNotEmpty() && getTableName().isNotEmpty() -> "${getSchemaName()}.${getTableName()}"
            getTableName().isNotEmpty() -> getTableName()
            else -> ""
        }
    }

}
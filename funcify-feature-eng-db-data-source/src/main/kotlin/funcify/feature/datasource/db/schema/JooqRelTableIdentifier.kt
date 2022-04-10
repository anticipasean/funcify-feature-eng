package funcify.feature.datasource.db.schema


/**
 *
 * @author smccarron
 * @created 2/27/22
 */
data class JooqRelTableIdentifier(override val catalogName: String = "",
                                  override val schemaName: String = "",
                                  override val tableName: String = "") : RelTableIdentifier {


    companion object {

        @JvmStatic
        fun fromJooqRelTable(jooqRelTable: JooqRelTable): JooqRelTableIdentifier {
            return JooqRelTableIdentifier(catalogName = jooqRelTable.catalog?.name
                                                        ?: "",
                                          schemaName = jooqRelTable.schema?.name
                                                       ?: "",
                                          tableName = jooqRelTable.name)
        }

    }

}

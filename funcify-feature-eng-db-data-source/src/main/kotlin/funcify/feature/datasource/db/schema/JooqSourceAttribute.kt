package funcify.feature.datasource.db.schema

import arrow.core.filterIsInstance
import arrow.core.getOrElse
import arrow.core.toOption
import funcify.feature.datasource.reldb.RelDatabaseSourceAttribute
import funcify.feature.datasource.reldb.RelTableIdentifier
import funcify.feature.schema.SchematicPath
import funcify.feature.schema.path.DefaultSchematicPath
import org.jooq.Record
import org.jooq.TableField


/**
 *
 * @author smccarron
 * @created 2/27/22
 */
data class JooqSourceAttribute(val jooqTableField: TableField<Record, *>) : RelDatabaseSourceAttribute {
    override val columnName: String = jooqTableField.name
    override val relTableIdentifier: RelTableIdentifier by lazy {
        jooqTableField.table.toOption()
                .filterIsInstance<JooqRelTable>()
                .map { t -> JooqRelTableIdentifier.fromJooqRelTable(t) }
                .getOrElse { JooqRelTableIdentifier() }
    }
    override val name: String = jooqTableField.name
    override val canonicalPath: SchematicPath = DefaultSchematicPath()
}

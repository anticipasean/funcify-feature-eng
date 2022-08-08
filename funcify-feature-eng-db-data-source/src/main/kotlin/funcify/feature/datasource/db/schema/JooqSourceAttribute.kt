package funcify.feature.datasource.db.schema

import arrow.core.filterIsInstance
import arrow.core.getOrElse
import arrow.core.toOption
import funcify.feature.naming.ConventionalName
import funcify.feature.naming.impl.DefaultConventionalName
import funcify.feature.naming.impl.DefaultNameSegment
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.extensions.StringExtensions.flatten
import kotlinx.collections.immutable.persistentListOf
import org.jooq.Record
import org.jooq.TableField

/**
 *
 * @author smccarron
 * @created 2/27/22
 */
data class JooqSourceAttribute(val jooqTableField: TableField<Record, *>) :
    RelDatabaseSourceAttribute {
    override val columnName: String = jooqTableField.name
    override val relTableIdentifier: RelTableIdentifier by lazy {
        jooqTableField
            .table
            .toOption()
            .filterIsInstance<JooqRelTable>()
            .map { t -> JooqRelTableIdentifier.fromJooqRelTable(t) }
            .getOrElse { JooqRelTableIdentifier() }
    }
    override val name: ConventionalName by lazy {
        DefaultConventionalName(
            "JOOQ_COLUMN_NAME",
            persistentListOf(DefaultNameSegment(jooqTableField.name))
        )
    }
    override val sourcePath: SchematicPath =
        SchematicPath.getRootPath().transform {
            pathSegment(
                jooqTableField.table?.catalog?.name ?: "",
                jooqTableField.table?.schema?.name ?: "",
                jooqTableField.table?.name ?: "",
                jooqTableField.name
            )
        }
    override val dataSourceLookupKey: DataSource.Key<RelDatabaseSourceIndex> by lazy {
        JooqDataSourceKey(
            jooqTableField.table?.catalog?.name
                ?: throw IllegalArgumentException(
                    """cannot create data_source.key for absent 
                    |catalog name for relational_database_index
                    |""".flatten()
                )
        )
    }
}

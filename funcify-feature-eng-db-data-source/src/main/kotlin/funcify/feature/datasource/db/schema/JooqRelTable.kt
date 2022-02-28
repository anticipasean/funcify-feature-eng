package funcify.feature.datasource.db.schema

import org.jooq.Comment
import org.jooq.Field
import org.jooq.ForeignKey
import org.jooq.Name
import org.jooq.Record
import org.jooq.Schema
import org.jooq.Table
import org.jooq.TableOptions
import org.jooq.impl.DSL
import org.jooq.impl.TableImpl


/**
 *
 * @author smccarron
 * @created 2/27/22
 */
class JooqRelTable(name: Name?,
                   schema: Schema?,
                   child: Table<*>?,
                   path: ForeignKey<*, Record>?,
                   aliased: Table<Record>?,
                   parameters: Array<out Field<*>> = arrayOf(),
                   comment: Comment = DSL.comment(""),
                   options: TableOptions = TableOptions.table()) : TableImpl<Record>(name,
                                                                                     schema,
                                                                                     child,
                                                                                     path,
                                                                                     aliased,
                                                                                     parameters,
                                                                                     comment,
                                                                                     options) {


}

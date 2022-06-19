package funcify.feature.schema.datasource

import funcify.feature.schema.path.SchematicPath
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet


/**
 * A mapping of schematic paths for a given data source to their
 * representations as container types e.g. lists, maps, and/or objects
 * and attributes thereof e.g. numeric elements of lists ( [0]=1,[1]=5,[2]=7 ),
 * string keys and object values ("name"="Bob","id"=1232 ),
 * Show(name="Stranger Things",rating=4.3)
 * @author smccarron
 * @created 4/9/22
 */
interface SourceMetamodel<SI : SourceIndex<SI>> {

    val sourceIndicesByPath: ImmutableMap<SchematicPath, ImmutableSet<SI>>

}

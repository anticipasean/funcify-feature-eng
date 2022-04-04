package funcify.feature.schema

import funcify.naming.NamingConvention


/**
 *
 * @author smccarron
 * @created 4/3/22
 */
interface MetamodelGraphFactory {

    interface IndexTypeInputSpec {

        fun <I : Any> expectingInputOfType(): NamingConventionSpec<I>
    }

    interface NamingConventionSpec<I : Any> {

        fun nameContainerTypesWith(namingConvention: NamingConvention<I>)

    }


}


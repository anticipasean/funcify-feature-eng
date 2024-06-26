package funcify.feature.materializer.type

import graphql.schema.TypeResolver
import graphql.schema.idl.InterfaceWiringEnvironment

/**
 * @author smccarron
 * @created 2023-07-23
 */
interface MaterializationInterfaceSubtypeResolverFactory {

    fun providesTypeResolver(environment: InterfaceWiringEnvironment): Boolean

    fun createTypeResolver(environment: InterfaceWiringEnvironment): TypeResolver
}

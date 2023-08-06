package funcify.feature.materializer.graph

/**
 *
 * @author smccarron
 * @created 2023-08-06
 */
sealed interface QueryComponentContext {

    interface OperationDefinitionComponentContext : QueryComponentContext {

    }

    interface FieldComponentContext : QueryComponentContext {

    }

    interface FieldArgumentComponentContext : QueryComponentContext {

    }

    interface InlineFragmentFieldComponentContext : QueryComponentContext {

    }

    interface FragmentSpreadFieldComponentContext : QueryComponentContext {

    }

}

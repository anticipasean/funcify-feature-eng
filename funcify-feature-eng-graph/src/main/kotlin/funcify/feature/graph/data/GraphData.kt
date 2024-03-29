package funcify.feature.graph.data

/**
 * Internal type used to store the particular vertex and edge information for the graph
 *
 * Contents will vary depending on the constraints placed on the graph represented: parallelizable
 * edges
 * @param WT
 * - Witness type parameter
 * @param P
 * - Point type parameter
 * @param V
 * - Vertex type parameter
 * @param E
 * - Edge type parameter
 */
internal interface GraphData<WT, out P, out V, out E> {}

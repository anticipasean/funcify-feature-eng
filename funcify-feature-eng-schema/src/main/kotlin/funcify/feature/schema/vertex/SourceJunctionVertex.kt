package funcify.feature.schema.vertex

/**
 * Represents both an object type and an attribute of some object type within the schema
 *
 * As an object type, it can be decomposed into its child attribute vertices: e.g.
 * `application.applicants -> applicants/applicant_id?applicant_id= -> [ 123, 345 ]`
 *
 * As an attribute, it can be extracted from or assessed in relation to a parent type vertex: e.g.
 * `application.applicants -> size("application/applicants") -> 2`
 *
 * Hence, it has two representations, one as a [SourceContainerTypeVertex] and one as a
 * [SourceAttributeVertex]
 *
 * @author smccarron
 * @created 1/30/22
 */
interface SourceJunctionVertex : SourceContainerTypeVertex, SourceAttributeVertex

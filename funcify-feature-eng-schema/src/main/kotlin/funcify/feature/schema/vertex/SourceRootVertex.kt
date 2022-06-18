package funcify.feature.schema.vertex

/**
 * Represents a top-level domain within the context of the schema It must represent an object type
 * and not an attribute within the schema since a scalar attribute would not represent anything
 * meaningful without some context: e.g. `123 -> user/user_id` where `user` is the root type vertex
 * vs. `123 -> id` where `id` is the root type vertex
 * @author smccarron
 * @created 1/30/22
 */
interface SourceRootVertex : SourceContainerTypeVertex

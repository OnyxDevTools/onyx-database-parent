package com.onyx.persistence.annotations

/**
 * Annotation used to indicate a class that is specified as a managed entity.
 *
 * Also, in order to be a managed entity the class must extend the com.onyx.persistence.ManagedEntity class
 *
 * @author Tim Osborn
 * @since 1.0.0
 *
 * <pre>
 *
 * @Entity
 *
 * @Entity
 * public class MyEntity extends ManagedEntity
 * {
 * ...
 * }
 *
 * </pre>
 *
 * @see com.onyx.persistence.ManagedEntity
 */

@Target(AnnotationTarget.CLASS)
annotation class Entity(
    val fileName: String = "",
    val archiveDirectories: Array<String> = [],
    /** Fingerprint width used by vector-managed structured and semantic features. */
    val entropy: Int = 128,
    /** Whole-record search capabilities exposed by a [com.onyx.persistence.VectorManagedEntity]. */
    val searchSupport: SearchSupport = SearchSupport.BOTH,
)

/** Search indexes a vector-managed entity elects to build and expose. */
enum class SearchSupport {
    /** Build and expose normalized lexical term routes only. */
    LEXICAL,

    /** Build and expose an automatically embedded semantic HNSW index only. */
    SEMANTIC,

    /** Build both indexes; this is the backwards-compatible default. */
    BOTH;

    internal val supportsLexical: Boolean
        get() = this == LEXICAL || this == BOTH

    internal val supportsSemantic: Boolean
        get() = this == SEMANTIC || this == BOTH
}

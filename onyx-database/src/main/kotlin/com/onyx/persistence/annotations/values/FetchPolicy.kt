package com.onyx.persistence.annotations.values

/**
 * The fetch policy of a relationship
 *
 * EAGER - Fetch all values when hydrating an entity
 *
 * LAZY - Fetch reference that is used to hydrate a relationship entity within a collection when
 * needed.
 *
 * NONE - Do not fetch relationship objects
 *
 * @author Tim Osborn
 * @since 1.0.0
 *
 * <pre>
 *
 *
 * @Relationship(name = "children" fetchPolicy = FetchPolicy.EAGER)
 * protected List<Children> myChildren
 *
 * </pre> *
 *
 * @see com.onyx.persistence.annotations.Relationship
 */
enum class FetchPolicy {
    EAGER,LAZY,NONE
}

package com.onyx.persistence.annotations;

/**
 * The fetch policy of a relationship
 *
 * EAGER - Fetch all values when hydrating an entity
 *
 * LAZY - Fetch reference that is used to hydrate a relationship entity within a collection when
 *        needed.
 *
 * NONE - Do not fetch relationship objects
 *
 * @author Tim Osborn
 * @since 1.0.0
 *
 * <pre>
 * <code>
 *
 *     {@literal @}Relationship(name = "children" fetchPolicy = FetchPolicy.EAGER)
 *      protected List{@literal <Children>} myChildren
 *
 * </code>
 * </pre>
 *
 * @see com.onyx.persistence.annotations.Relationship
 */
public enum FetchPolicy
{
    EAGER,
    LAZY,
    NONE;

    /**
     * Constructor
     */
    @SuppressWarnings("unused")
    FetchPolicy()
    {

    }

}

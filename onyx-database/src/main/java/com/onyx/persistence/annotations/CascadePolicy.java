package com.onyx.persistence.annotations;

/**
 * Policy for maintaining the save or delete status for relationship
 *
 * @author Tim Osborn
 * @since 1.0.0
 *
 * SAVE - Save child relationship object(s) upon saving parent
 * DELETE - Delete child relationship object(s) upon deleting parent
 * ALL - Delete or Save relationship object(s) upon performing Save or Delete
 * DEFER_SAVE - Defer save relationships is used to optimize persistence upon batch saving.
 * NONE - Do not perform cascading on related entities
 *
 * <pre>
 * <code>
 *{@literal @}Entity
 * public class Person extends ManagedEntity
 * {
 *     {@literal @}Relationship(name = "children" cascadePolicy = CascadePolicy.ALL)
 *      protected List{@literal <Children>} myChildren
 * }
 *
 * </code>
 * </pre>
 *
 * @see com.onyx.persistence.annotations.Relationship
 *
 */
public enum CascadePolicy
{

    SAVE,
    DELETE,
    ALL,
    DEFER_SAVE,
    NONE;

    /**
     * Constructor
     */
    @SuppressWarnings("unused")
    CascadePolicy()
    {

    }
}

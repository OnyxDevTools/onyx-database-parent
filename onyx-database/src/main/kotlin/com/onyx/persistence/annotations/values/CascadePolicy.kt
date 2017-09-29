package com.onyx.persistence.annotations.values

/**
 * Policy for maintaining the save or delete status for relationship
 *
 * @author Tim Osborn
 * @since 1.0.0
 *
 * SAVE - Save child relationship value(s) upon saving parent
 * DELETE - Delete child relationship value(s) upon deleting parent
 * ALL - Delete or Save relationship value(s) upon performing Save or Delete
 * DEFER_SAVE - Defer save relationships is used to optimize persistence upon batch saving.
 * NONE - Do not perform cascading on related entities
 *
 * <pre>
 *
 * @Entity
 * public class Person extends ManagedEntity
 * {
 *      @Relationship(name = "children" cascadePolicy = CascadePolicy.ALL)
 *      protected List<Children> myChildren
 * }
 *
 * </pre>
 *
 * @see com.onyx.persistence.annotations.Relationship
 */
enum class CascadePolicy {
    SAVE,DELETE,ALL,DEFER_SAVE,NONE
}

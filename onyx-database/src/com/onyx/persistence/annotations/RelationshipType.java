package com.onyx.persistence.annotations;

/**
 * The relationship type enforces constraints to the relationship regarding how many objects can be related
 *
 * ONE_TO_ONE - Strict one to one relationship enforcement
 *
 * ONE_TO_MANY - One entity to many entities
 *
 * MANY_TO_MANY - Many to Many relationships
 *
 * @author Tim Osborn
 * @since 1.0.0
 *
 * <pre>
 *     <code>
 *      // One To One Relationship
 *      {@literal @}elationship(name = "children" fetchPolicy = RelationshipType.MANY_TO_MANY)
 *       protected {@literal <Children>} myChildren
 *     </code>
 * </pre>
 *
 * @see com.onyx.persistence.annotations.Relationship
 *
 */
public enum RelationshipType
{
    ONE_TO_ONE,
    ONE_TO_MANY,
    MANY_TO_ONE,
    MANY_TO_MANY;


    /**
     * Constructor
     */
    RelationshipType()
    {

    }

}

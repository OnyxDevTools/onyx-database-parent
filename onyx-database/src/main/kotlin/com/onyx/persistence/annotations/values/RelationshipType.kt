package com.onyx.persistence.annotations.values

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
 * // One To One Relationship
 * @elationship(name = "children" fetchPolicy = RelationshipType.MANY_TO_MANY)
 * protected <Children> myChildren
 *
 * @see com.onyx.persistence.annotations.Relationship
 */
enum class RelationshipType {
    ONE_TO_ONE,ONE_TO_MANY,MANY_TO_ONE,MANY_TO_MANY
}

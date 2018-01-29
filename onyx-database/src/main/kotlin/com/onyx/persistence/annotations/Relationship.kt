package com.onyx.persistence.annotations

import com.onyx.persistence.annotations.values.CascadePolicy
import com.onyx.persistence.annotations.values.FetchPolicy
import com.onyx.persistence.annotations.values.RelationshipType
import kotlin.reflect.KClass

/**
 * This annotation is used to indicate an attribute within a ManagedEntity
 *
 * Currently supported attribute types include (Long, long, Integer, int, Double, double, Float, float, String)
 *
 * @author Tim Osborn
 *
 * @since 1.0.0
 *
 * <pre>
 *     <code>
 *      // One To One Relationship
 *      {@literal @}Relationship(type = RelationshipType.ONE_TO_ONE,
 *                     cascadePolicy = CascadePolicy.NONE,
 *                     inverseClass = OneToOneParent.class,
 *                     inverse = "child")
 *       public OneToOneParent parent;
 *
 *      // Many To Many Relationship
 *      {@literal @}Relationship(type = RelationshipType.MANY_TO_MANY,
 *                     cascadePolicy = CascadePolicy.ALL,
 *                     fetchPolicy = FetchPolicy.LAZY,
 *                     inverse = "childNoCascade",
 *                     inverseClass = ManyToManyParent.class)
 *       public List{@literal <ManyToManyParent>} parentCascade;
 *     </code>
 * </pre>
 *
 *
 * @see com.onyx.persistence.annotations.values.FetchPolicy
 * @see com.onyx.persistence.annotations.values.CascadePolicy
 * @see com.onyx.persistence.annotations.values.RelationshipType
 */
@Target(AnnotationTarget.FIELD)
annotation class Relationship(
        /**
     * This indicates the relationship type.
     * It can either be One To One, Many To One, One To Many, or Many to Many
     *
     * @since 1.0.0
     * @see RelationshipType
     *
     *
     * @return Relationship Type
     */
    val type: RelationshipType,

        /**
     * Specify the relationship inverse type.
     *
     * @since 1.0.0
     * @return Class that relationship objects are declared as
     */
    val inverseClass: KClass<*>,

        /**
     * Inverse relationship property name
     *
     * @since 1.0.0
     * @return Inverse name as identified on the inverse class
     */
    val inverse: String = "",

        /**
     * Fetch policy for the relationship.  This will determine how the relationship objects are hydrated
     *
     * The fetch policy can be either Lazy, None, or Eager
     *
     * @since 1.0.0
     * @see FetchPolicy
     *
     *
     * @return Fetch Policy for relationship
     */
    val fetchPolicy: FetchPolicy = FetchPolicy.LAZY,

        /**
     * Cascade policy whether to save the child when you are saving the parent entity
     * The cascade policy can either be None, Save, Delete, Defer Save
     *
     * Defer Save is used under conditions of batch saving.  This is done in order to optimize the persisting of records and reduce the amount of transactions performed when saving a collection of entities
     *
     * @since 1.0.0
     * @see CascadePolicy
     *
     *
     * @return The relationships Cascade Policy
     */
    val cascadePolicy: CascadePolicy = CascadePolicy.NONE,

        /**
     * Getter for the load factor
     * @return The values are from 1-10.
     *
     * 1 is the fastest for small data sets.  10 is to span huge data sets intended that the performance of the index
     * does not degrade over time.  Note: You can not change this ad-hoc.  You must re-build the index if you intend
     * to change.  Always plan for scale when designing your data model.
     *
     * This defaults to 2 in order to account for efficiency for smaller data sets.  The footprint will be smaller
     * the smaller the value.
     *
     * @since 1.2.0
     */
    val loadFactor: Int = 2)

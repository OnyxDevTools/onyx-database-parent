package com.onyx.persistence.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation is used to indicate a class property as an indexed attribute
 *
 * Note: This must also include an com.onyx.persistence.annotations.Attribute annotation
 *
 * @author Tim Osborn
 *
 * @since 1.0.0
 *
 * <pre>
 *     <code>
 *      {@literal @}Index
 *      {@literal @}Attribute(nullable = false, size = 200)
 *       public long personID;
 *     </code>
 * </pre>
 *
 * @see com.onyx.persistence.annotations.Identifier
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Index
{

    /**
     * Getter for the load factor
     * @return The values are from 1-10.
     *
     * 1 is the fastest for small data sets.  10 is to span huge data sets intended that the performance of the index
     * does not degrade over time.  Note: You can not change this ad-hoc.  You must re-build the index if you intend
     * to change.  Always plan for scale when designing your data model.
     *
     * This defaults to 5 in order to account for efficiency for smaller data sets.  The footprint will be smaller
     * the smaller the value.
     *
     * @since 1.2.0
     */
    @SuppressWarnings("unused") byte loadFactor() default 5;

}

package com.onyx.persistence.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation is used to indicate an attribute as a primary key
 *
 * Note: The @Attribute annotation is still required in order to indicate a primary key
 *
 * @author Tim Osborn
 *
 * @since 1.0.0
 *
 *
 * <pre>
 * <code>
 *
 *      {@literal @}Identifier(generator = IdentifierGenerator.SEQUENCE)
 *      {@literal @}Attribute(nullable = false, size = 200)
 *       public long personID;
 *
 * </code>
 * </pre>
 *
 * @see com.onyx.persistence.annotations.Attribute
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Identifier
{
    /**
     * Determines the generator mode.  By default the primary key is not auto generated.
     * If a sequence identifier generator is selected you must declare the property as numeric.
     *
     * @since 1.0.0
     * @return Generator Type
     */
    @SuppressWarnings("unused") IdentifierGenerator generator() default IdentifierGenerator.NONE;

    /**
     * This method is to determine what scale the underlying structure should be.  The values are from 1-10.
     * 1 is the fastest for small data sets.  10 is to span huge data sets intended that the performance of the index
     * does not degrade over time.  Note: You can not change this ad-hoc.  You must re-build the index if you intend
     * to change.  Always plan for scale when designing your data model.
     *
     * Value from 1-10.  The default is 5
     *
     * @since 1.2.0
     */
    @SuppressWarnings("unused") int loadFactor() default 5;
}

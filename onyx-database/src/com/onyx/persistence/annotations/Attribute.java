package com.onyx.persistence.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

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
 * <code>
 *{@literal @}Entity
 * public class Person extends ManagedEntity
 * {
 *     {@literal @}Attribute(nullable = false, size = 200)
 *      public String firstNameAttribute;
 * }
 *
 * </code>
 * </pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Attribute
{
    /**
     * Determines whether the attribute is nullable
     *
     * @since 1.0.0
     * @return Boolean key indicating nullable or not
     */
    @SuppressWarnings("unused") boolean nullable() default true;

    /**
     * Size of an attribute.  Only applies if the attribute is type string
     *
     * @since 1.0.0
     * @return Attribute max size
     */
    @SuppressWarnings("unused") int size() default -1;
}

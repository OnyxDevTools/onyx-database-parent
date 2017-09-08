package com.onyx.persistence.annotations

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
 * `
 * @Entity
 * public class Person extends ManagedEntity
 * {
 *      @Attribute(nullable = false, size = 200)
 *      public String firstNameAttribute;
 * }
 *
 * </pre>
 */
@Target(AnnotationTarget.FIELD)
annotation class Attribute(

    /**
     * Determines whether the attribute is nullable
     *
     * @since 1.0.0
     * @return Boolean key indicating nullable or not
     */
    val nullable: Boolean = true,
    /**
     * Size of an attribute.  Only applies if the attribute is type string
     *
     * @since 1.0.0
     * @return Attribute max size
     */
    val size: Int = -1)

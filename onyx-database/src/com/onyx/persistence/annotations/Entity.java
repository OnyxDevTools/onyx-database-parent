package com.onyx.persistence.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to indicate a class that is specified as a managed entity.
 *
 * Also, in order to be a managed entity the class must extend the com.onyx.persistence.ManagedEntity class
 *
 * @author Tim Osborn
 * @since 1.0.0
 *
 * <pre>
 * <code>
 *{@literal @}Entity
 *
 *     {@literal @}Entity
 *      public class MyEntity extends ManagedEntity
 *      {
 *          ...
 *      }
 *
 * </code>
 * </pre>
 *
 * @see com.onyx.persistence.ManagedEntity
 */

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Entity
{
    /**
     * Specifies whether the entity data is cached.  i.e. a memory mapped file
     *
     * @return Cachable Indicator
     */
    @SuppressWarnings("unused") boolean cachable() default false;

    /**
     * The maximum cache size in records
     *
     * @since 1.0.0
     * @return Cache Size in records
     */
    @SuppressWarnings("unused") int cacheSize() default 0; // Cache Size in Records

    /**
     * File in which the data lives
     *
     * @since 1.0.0
     * @return File Name
     */
    @SuppressWarnings("unused") String fileName() default "";

}

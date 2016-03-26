package com.onyx.persistence.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * This annotation is used to indicate method that is invoked before inserting or updating a ManagedEntity.
 *
 *
 * @author Tim Osborn
 *
 * @since 1.0.0
 *
 * <pre>
 *     <code>
 *      {@literal @}Attribute
 *       protected Date persistedDate = null;
 *
 *      {@literal @}PreInsert
 *       public void onPrePersist()
 *       {
 *           this.persistedDate = new Date(); // Modify the insertion Date
 *       }
 *     </code>
 * </pre>
 * @see com.onyx.persistence.ManagedEntity
 *
 */
@Target({METHOD})
@Retention(RUNTIME)
public @interface PrePersist
{
}

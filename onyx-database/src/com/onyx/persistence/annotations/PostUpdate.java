package com.onyx.persistence.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * This annotation is used to indicate method that is invoked after updating a ManagedEntity.
 *
 *
 * @author Tim Osborn
 *
 * @since 1.0.0
 *
 * <pre>
 *     <code>
 *      {@literal @}PostUpdate
 *       public void onPostUpdate()
 *       {
 *           Log("Updated Object with id " + this.id);
 *       }
 *     </code>
 * </pre>
 *
 * @see com.onyx.persistence.ManagedEntity
 *
 */
@Target({METHOD})
@Retention(RUNTIME)
public @interface PostUpdate
{
}

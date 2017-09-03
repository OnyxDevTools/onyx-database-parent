package com.onyx.persistence.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * This annotation is used to indicate method that is invoked after loading a ManagedEntity.
 *
 *
 * @author Tim Osborn
 *
 * @since 1.0.0
 *
 * <pre>
 *     <code>
 *      {@literal @}PostLoad
 *       public void onPostLoad()
 *       {
 *           this.entityDescription = "Loaded " + this.entityDescription;
 *       }
 *     </code>
 * </pre>
 * @see com.onyx.persistence.ManagedEntity
 *
 */
@SuppressWarnings("unused")
@Deprecated
@Target({METHOD})
@Retention(RUNTIME)
public @interface PostLoad {
}

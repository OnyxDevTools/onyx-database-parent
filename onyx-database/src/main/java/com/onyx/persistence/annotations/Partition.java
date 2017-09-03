package com.onyx.persistence.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation is used to indicate a property to partition the ManagedEntities by.
 *
 * This will change how the data is being accessed by the Onyx Database in order to optimize performance on large data sets.
 *
 * Note: This must also include an com.onyx.persistence.annotations.Attribute annotation
 *
 * @author Tim Osborn
 *
 * @since 1.0.0
 *
 *
 * <pre>
 *     <code>
 *      {@literal @}Partition
 *      {@literal @}Attribute(nullable = false, size = 200)
 *       public long personID;
 *     </code>
 * </pre>
 *
 * @see com.onyx.persistence.annotations.Attribute
 */
@SuppressWarnings("unused")
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Partition
{

}

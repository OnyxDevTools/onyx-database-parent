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

}

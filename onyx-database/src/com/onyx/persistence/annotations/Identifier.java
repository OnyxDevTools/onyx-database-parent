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
    IdentifierGenerator generator() default IdentifierGenerator.NONE;
}

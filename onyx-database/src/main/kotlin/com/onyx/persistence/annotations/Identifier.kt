package com.onyx.persistence.annotations

import com.onyx.persistence.annotations.values.IdentifierGenerator

/**
 * This annotation is used to indicate an attribute as a primary key
 *
 *
 * Note: The @Attribute annotation is still required in order to indicate a primary key
 *
 * @author Tim Osborn
 * @see com.onyx.persistence.annotations.Attribute
 *
 * @since 1.0.0
 *
 * <pre>
 *
 * @Identifier(generator = IdentifierGenerator.SEQUENCE)
 * @Attribute(nullable = false, size = 200)
 * public long personID;
 *
 * </pre>
 */
@Target(AnnotationTarget.FIELD)
annotation class Identifier(
        /**
         * Determines the generator mode.  By default the primary key is not auto generated.
         * If a sequence identifier generator is selected you must declare the property as numeric.
         *
         * @return Generator Type
         * @since 1.0.0
         */
        val generator: IdentifierGenerator = IdentifierGenerator.NONE
)

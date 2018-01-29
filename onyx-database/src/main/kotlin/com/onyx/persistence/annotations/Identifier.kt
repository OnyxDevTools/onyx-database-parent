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
        val generator: IdentifierGenerator = IdentifierGenerator.NONE,
        /**
         * This method is to determine what scale the underlying structure should be.  The values are from 1-10.
         * 1 is the fastest for small data sets.  10 is to span huge data sets intended that the performance of the index
         * does not degrade over time.  Note: You can not change this ad-hoc.  You must re-build the index if you intend
         * to change.  Always plan for scale when designing your data model.
         *
         *
         * Value from 1-10.  The default is 2
         *
         * @since 1.2.0
         */
        val loadFactor: Int = 2)

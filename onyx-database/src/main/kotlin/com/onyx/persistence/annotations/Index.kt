package com.onyx.persistence.annotations

/**
 * This annotation is used to indicate a class property as an indexed attribute
 *
 * Note: This must also include an com.onyx.persistence.annotations.Attribute annotation
 *
 * @author Tim Osborn
 *
 * @since 1.0.0
 *
 * @Index
 * @Attribute(nullable = false, size = 200)
 * public long personID;
 *
 * @see com.onyx.persistence.annotations.Identifier
 */
@Target(AnnotationTarget.FIELD)
annotation class Index(
        /**
         * Getter for the load factor
         * @return The values are from 1-10.
         *
         * 1 is the fastest for small data sets.  10 is to span huge data sets intended that the performance of the index
         * does not degrade over time.  Note: You can not change this ad-hoc.  You must re-build the index if you intend
         * to change.  Always plan for scale when designing your data model.
         *
         * This defaults to 2 in order to account for efficiency for smaller data sets.  The footprint will be smaller
         * the smaller the value.
         *
         * @since 1.2.0
         */
        val loadFactor: Byte = 2)

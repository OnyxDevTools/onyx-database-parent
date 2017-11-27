package com.onyx.persistence.annotations

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
 * @Partition
 * @Attribute(nullable = false, size = 200)
 * public long personID;
 *
 * @see com.onyx.persistence.annotations.Attribute
 */
@Target(AnnotationTarget.FIELD)
annotation class Partition

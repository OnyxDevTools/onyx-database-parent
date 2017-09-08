package com.onyx.persistence.annotations

/**
 * This annotation is used to indicate method that is invoked before inserting or updating a ManagedEntity.
 *
 *
 * @author Tim Osborn
 *
 * @since 1.0.0
 *
 * @Attribute
 * protected Date persistedDate = null;
 *
 * @PreInsert
 * public void onPrePersist()
 * {
 *      this.persistedDate = new Date(); // Modify the insertion Date
 * }
 *
 * @see com.onyx.persistence.ManagedEntity
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
annotation class PrePersist

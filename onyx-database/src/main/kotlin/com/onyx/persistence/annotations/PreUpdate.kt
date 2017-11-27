package com.onyx.persistence.annotations

/**
 * This annotation is used to indicate method that is invoked before updating a ManagedEntity.  This does not include inserting entities.
 *
 *
 * @author Tim Osborn
 *
 * @since 1.0.0
 *
 * @Attribute
 * protected Date lastUpdated = null;
 *
 * @PreUpdate
 * public void onPreUpdate()
 * {
 *      this.lastUpdated = new Date(); // Modify the last updated Date
 * }
 *
 *
 * @see com.onyx.persistence.ManagedEntity
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
annotation class PreUpdate

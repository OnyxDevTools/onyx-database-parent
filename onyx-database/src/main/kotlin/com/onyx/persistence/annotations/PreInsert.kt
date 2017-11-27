package com.onyx.persistence.annotations

/**
 * This annotation is used to indicate method that is invoked before inserting a ManagedEntity.  This does not include updated entities.
 *
 *
 * @author Tim Osborn
 *
 * @since 1.0.0
 *
 * @Attribute
 * protected Date insertedDate = null;
 *
 * @PreInsert
 * public void onPreInsert()
 * {
 *      this.insertedDate = new Date(); // Modify the insertion Date
 * }
 *
 * @see com.onyx.persistence.ManagedEntity
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
annotation class PreInsert

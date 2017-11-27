package com.onyx.persistence.annotations

/**
 * This annotation is used to indicate method that is invoked after inserting a ManagedEntity.  This does not include updated entities.
 *
 *
 * @author Tim Osborn
 *
 * @since 1.0.0
 *
 * @PostInsert
 * public void onPostInsert()
 * {
 *      this.insertedDate = new Date(); // Modify the insertion Date
 * }
 *
 * @see com.onyx.persistence.ManagedEntity
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
annotation class PostInsert

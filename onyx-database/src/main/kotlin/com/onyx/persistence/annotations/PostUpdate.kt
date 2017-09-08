package com.onyx.persistence.annotations

/**
 * This annotation is used to indicate method that is invoked after updating a ManagedEntity.
 *
 *
 * @author Tim Osborn
 *
 * @since 1.0.0
 *
 * @PostUpdate
 * public void onPostUpdate()
 * {
 *      Log("Updated Object with id " + this.id);
 * }
 *
 * @see com.onyx.persistence.ManagedEntity
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
annotation class PostUpdate

package com.onyx.persistence.annotations

/**
 * This annotation is used to indicate method that is invoked after deleting a ManagedEntity.
 *
 *
 * @author Tim Osborn
 *
 * @since 1.0.0
 *
 * @PostRemove
 * public void onPostRemove()
 * {
 *      persistedObjects --;
 * }
 *
 * @see com.onyx.persistence.ManagedEntity
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
annotation class PostRemove

package com.onyx.persistence.annotations

/**
 * This annotation is used to indicate method that is invoked before removing a ManagedEntity.
 *
 *
 * @author Tim Osborn
 *
 * @since 1.0.0
 *
 * @Attribute
 * protected Date persistedDate = null;
 *
 * @PreRemove
 * public void onPreRemove()
 * {
 *      Cache.MyCache.remove(this);
 * }
 *
 * @see com.onyx.persistence.ManagedEntity
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
annotation class PreRemove

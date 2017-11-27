package com.onyx.extension

import com.onyx.descriptor.EntityDescriptor
import com.onyx.exception.EntityCallbackException
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.context.SchemaContext

/**
 * Invoke Pre Persist callback on entity
 *
 * @throws EntityCallbackException Error happened during callback
 */
@Throws(EntityCallbackException::class)
fun IManagedEntity.onPrePersist(context: SchemaContext, descriptor: EntityDescriptor = descriptor(context)) {
    if(this is ManagedEntity && !this.ignoreListeners) {
        try {
            descriptor.prePersistCallback?.invoke(this)
        } catch (e: Exception) {
            throw EntityCallbackException(descriptor.prePersistCallback!!.name, EntityCallbackException.INVOCATION, e)
        }
    }
}

/**
 * Invoke Pre Insert callback
 *
 * @throws EntityCallbackException Error happened during callback
 */
@Throws(EntityCallbackException::class)
fun IManagedEntity.onPreInsert(context: SchemaContext, descriptor: EntityDescriptor = descriptor(context)) {
    this.onPrePersist(context, descriptor)
    if(this is ManagedEntity && !this.ignoreListeners) {
        try {
            descriptor.preInsertCallback?.invoke(this)
        } catch (e: Exception) {
            throw EntityCallbackException(descriptor.preInsertCallback!!.name, EntityCallbackException.INVOCATION, e)
        }
    }
}

/**
 * Invoke Pre Update Callback on entity
 *
 * @throws EntityCallbackException Error happened during callback
 */
@Throws(EntityCallbackException::class)
fun IManagedEntity.onPreUpdate(context: SchemaContext, descriptor: EntityDescriptor = descriptor(context)) {
    this.onPrePersist(context, descriptor)
    if(this is ManagedEntity && !this.ignoreListeners) {
        try {
            descriptor.preUpdateCallback?.invoke(this)
        } catch (e: Exception) {
            throw EntityCallbackException(descriptor.preUpdateCallback!!.name, EntityCallbackException.INVOCATION, e)
        }
    }
}

/**
 * Invoke Pre Remove callback on entity
 *
 * @throws EntityCallbackException Error happened during callback
 */
@Throws(EntityCallbackException::class)
fun IManagedEntity.onPreRemove(context: SchemaContext, descriptor: EntityDescriptor = descriptor(context)) {
    if(this is ManagedEntity && !this.ignoreListeners) {
        try {
            descriptor.preRemoveCallback?.invoke(this)
        } catch (e: Exception) {
            throw EntityCallbackException(descriptor.preRemoveCallback!!.name, EntityCallbackException.INVOCATION, e)
        }
    }
}

/**
 * Invoke Post Insert callback on entity
 *
 * @throws EntityCallbackException Error happened during callback
 */
@Throws(EntityCallbackException::class)
fun IManagedEntity.onPostInsert(context: SchemaContext, descriptor: EntityDescriptor = descriptor(context)) {
    onPostPersist(context, descriptor)
    if(this is ManagedEntity && !this.ignoreListeners) {
        try {
            descriptor.postInsertCallback?.invoke(this)
        } catch (e: Exception) {
            throw EntityCallbackException(descriptor.postInsertCallback!!.name, EntityCallbackException.INVOCATION, e)
        }
    }
}

/**
 * Invoke Post Update callback on entity
 *
 * @throws EntityCallbackException Error happened during callback
 */
@Throws(EntityCallbackException::class)
fun IManagedEntity.onPostUpdate(context: SchemaContext, descriptor: EntityDescriptor = descriptor(context)) {
    onPostPersist(context, descriptor)
    if(this is ManagedEntity && !this.ignoreListeners) {
        try {
            descriptor.postUpdateCallback?.invoke(this)
        } catch (e: Exception) {
            throw EntityCallbackException(descriptor.postUpdateCallback!!.name, EntityCallbackException.INVOCATION, e)
        }
    }
}

/**
 * Invoke Post Remove Callback on entity
 *
 * @throws EntityCallbackException Error happened during callback
 */
@Throws(EntityCallbackException::class)
fun IManagedEntity.onPostRemove(context: SchemaContext, descriptor: EntityDescriptor = descriptor(context)) {
    if(this is ManagedEntity && !this.ignoreListeners) {
        try {
            descriptor.postRemoveCallback?.invoke(this)
        } catch (e: Exception) {
            throw EntityCallbackException(descriptor.postRemoveCallback!!.name, EntityCallbackException.INVOCATION, e)
        }
    }
}

/**
 * Invoke Post Persist callback on entity
 *
 * @throws EntityCallbackException Error happened during callback
 */
@Throws(EntityCallbackException::class)
fun IManagedEntity.onPostPersist(context: SchemaContext, descriptor: EntityDescriptor = descriptor(context)) {
    if(this is ManagedEntity && !this.ignoreListeners) {
        try {
            descriptor.postPersistCallback?.invoke(this)
        } catch (e: Exception) {
            throw EntityCallbackException(descriptor.postPersistCallback!!.name, EntityCallbackException.INVOCATION, e)
        }
    }
}
package com.onyx.descriptor

import kotlin.jvm.internal.Intrinsics

/**
 * Created by timothy.osborn on 12/11/14.
 *
 * General information regarding an index within an entity
 */
open class IndexDescriptor(
    open var loadFactor: Byte = 1,
    override var name: String = "",
    open var type: Class<*> = Any::class.java
) : AbstractBaseDescriptor(), BaseDescriptor {

    open lateinit var entityDescriptor: EntityDescriptor

    override fun hashCode(): Int = (((this.entityDescriptor.entityClass.hashCode()) * 31 + this.loadFactor) * 31 + this.name.hashCode()) * 31 + this.type.hashCode()

    override fun equals(other: Any?): Boolean {
        return if (this !== other) {
            if (other is IndexDescriptor) {
                val var2 = other as IndexDescriptor?
                if (Intrinsics.areEqual(this.entityDescriptor.partition, var2!!.entityDescriptor.partition) && this.loadFactor == var2.loadFactor && Intrinsics.areEqual(this.name, var2.name) && Intrinsics.areEqual(this.type, var2.type)) {
                    return true
                }
            }

            false
        } else {
            true
        }
    }
}

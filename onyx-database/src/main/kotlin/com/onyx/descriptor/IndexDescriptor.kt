package com.onyx.descriptor

import com.onyx.extension.common.ClassMetadata
import com.onyx.persistence.annotations.values.IndexType
import kotlin.jvm.internal.Intrinsics

/**
 * Created by timothy.osborn on 12/11/14.
 *
 * General information regarding an index within an entity
 */
open class IndexDescriptor(
    override var name: String = "",
    open var type: Class<*> = ClassMetadata.ANY_CLASS,
    open var indexType: IndexType = IndexType.DEFAULT,
    open var embeddingDimensions: Int = -1,
    open var minimumScore: Float = -1f,
    open var hashTableCount: Int = -1
) : AbstractBaseDescriptor(), BaseDescriptor {

    open lateinit var entityDescriptor: EntityDescriptor

    override fun hashCode(): Int = (((this.entityDescriptor.entityClass.hashCode()) * 31) * 31 + this.name.hashCode()) * 31 + this.type.hashCode()

    override fun equals(other: Any?): Boolean {
        return if (this !== other) {
            if (other is IndexDescriptor) {
                val var2 = other as IndexDescriptor?
                if (Intrinsics.areEqual(this.entityDescriptor.partition, var2!!.entityDescriptor.partition) && Intrinsics.areEqual(this.name, var2.name) && Intrinsics.areEqual(this.type, var2.type)) {
                    return true
                }
            }

            false
        } else {
            true
        }
    }
}

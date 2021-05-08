package com.onyx.descriptor

import com.onyx.extension.common.ClassMetadata
import com.onyx.persistence.annotations.values.CascadePolicy
import com.onyx.persistence.annotations.values.FetchPolicy
import com.onyx.persistence.annotations.values.RelationshipType
import kotlin.jvm.internal.Intrinsics

/**
 * Created by timothy.osborn on 12/11/14.
 *
 * Details on an entity relationship
 */
data class RelationshipDescriptor(
        var inverse: String? = null,
        var parentClass: Class<*>,
        var relationshipType: RelationshipType,
        var inverseClass: Class<*>,
        var fetchPolicy: FetchPolicy,
        var cascadePolicy: CascadePolicy,
        var name: String = "",
        var type: Class<*> = ClassMetadata.ANY_CLASS
) : AbstractBaseDescriptor() {

    lateinit var entityDescriptor: EntityDescriptor

    override fun hashCode(): Int = ((((((((if (this.inverse != null) this.inverse!!.hashCode() else 0) * 31 + this.parentClass.hashCode()) * 31 + this.relationshipType.hashCode()) * 31 + this.inverseClass.hashCode()) * 31 + this.fetchPolicy.hashCode()) * 31 + this.cascadePolicy.hashCode()) * 31) * 31 + this.name.hashCode()) * 31 + this.type.hashCode()

    override fun equals(other: Any?): Boolean {
        if (this !== other) {
            if (other !is RelationshipDescriptor) {
                return false
            }

            val var2 = other as RelationshipDescriptor?
            if (!Intrinsics.areEqual(this.inverse, var2!!.inverse) || !Intrinsics.areEqual(this.parentClass, var2.parentClass) || !Intrinsics.areEqual(this.relationshipType, var2.relationshipType) || !Intrinsics.areEqual(this.inverseClass, var2.inverseClass) || !Intrinsics.areEqual(this.fetchPolicy, var2.fetchPolicy) || !Intrinsics.areEqual(this.cascadePolicy, var2.cascadePolicy) || !Intrinsics.areEqual(this.name, var2.name) || !Intrinsics.areEqual(this.type, var2.type)
                    && Intrinsics.areEqual(this.entityDescriptor.partition, var2.entityDescriptor.partition)) {
                return false
            }
        }

        return true
    }

    val isToMany:Boolean
        get() = relationshipType === RelationshipType.MANY_TO_MANY || relationshipType === RelationshipType.ONE_TO_MANY

    val isToOne:Boolean
        get() = !isToMany

    val shouldSaveEntity:Boolean
        get() = cascadePolicy === CascadePolicy.SAVE || cascadePolicy === CascadePolicy.ALL

    val shouldDeleteEntity:Boolean
        get() = (cascadePolicy === CascadePolicy.DELETE || cascadePolicy === CascadePolicy.ALL) && isToOne

    val shouldDeleteEntityReference:Boolean
        get() = ((cascadePolicy === CascadePolicy.DELETE || cascadePolicy === CascadePolicy.ALL) && isToMany) || isToOne

}

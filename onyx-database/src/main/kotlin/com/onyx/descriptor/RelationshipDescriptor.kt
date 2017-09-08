package com.onyx.descriptor

import com.onyx.persistence.annotations.CascadePolicy
import com.onyx.persistence.annotations.FetchPolicy
import com.onyx.persistence.annotations.RelationshipType
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
    var loadFactor: Byte = 1,
    var name: String = "",
    var type: Class<*> = Any::class.java
) : AbstractBaseDescriptor() {

    lateinit var entityDescriptor: EntityDescriptor

    override fun hashCode(): Int = ((((((((if (this.inverse != null) this.inverse!!.hashCode() else 0) * 31 + this.parentClass.hashCode()) * 31 + this.relationshipType.hashCode()) * 31 + this.inverseClass.hashCode()) * 31 + this.fetchPolicy.hashCode()) * 31 + this.cascadePolicy.hashCode()) * 31 + this.loadFactor) * 31 + this.name.hashCode()) * 31 + this.type.hashCode()

    override fun equals(other: Any?): Boolean {
        if (this !== other) {
            if (other !is RelationshipDescriptor) {
                return false
            }

            val var2 = other as RelationshipDescriptor?
            if (!Intrinsics.areEqual(this.inverse, var2!!.inverse) || !Intrinsics.areEqual(this.parentClass, var2.parentClass) || !Intrinsics.areEqual(this.relationshipType, var2.relationshipType) || !Intrinsics.areEqual(this.inverseClass, var2.inverseClass) || !Intrinsics.areEqual(this.fetchPolicy, var2.fetchPolicy) || !Intrinsics.areEqual(this.cascadePolicy, var2.cascadePolicy) || this.loadFactor != var2.loadFactor || !Intrinsics.areEqual(this.name, var2.name) || !Intrinsics.areEqual(this.type, var2.type)
                    && Intrinsics.areEqual(this.entityDescriptor.partition, var2.entityDescriptor.partition)) {
                return false
            }
        }

        return true
    }
}

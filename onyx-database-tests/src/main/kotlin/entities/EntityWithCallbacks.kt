package entities

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.annotations.*
import com.onyx.persistence.annotations.values.IdentifierGenerator

/**
 * Created by Chris Osborn on 12/26/2014.
 */
@Entity
@Suppress("UNUSED")
class EntityWithCallbacks : AbstractEntity(), IManagedEntity {

    @Identifier(generator = IdentifierGenerator.NONE)
    @Attribute(size = 255)
    var id: String? = null

    @Attribute(size = 255)
    var name: String? = null

    @PreInsert
    fun beforeInsert() {
        name = name!! + "_PreInsert"
    }

    @PreUpdate
    fun beforeUpdate() {
        name = name!! + "_PreUpdate"
    }

    @PrePersist
    fun beforePersist() {
        name = name!! + "_PrePersist"
    }

    @PreRemove
    fun beforeRemove() {
        name = name!! + "_PreRemove"
    }

    @PostInsert
    fun afterInsert() {
        name = name!! + "_PostInsert"
    }

    @PostUpdate
    fun afterUpdate() {
        name = name!! + "_PostUpdate"
    }

    @PostPersist
    fun afterPersist() {
        name = name!! + "_PostPersist"
    }

    @PostRemove
    fun afterRemove() {
        name = name!! + "_PostRemove"
    }

}

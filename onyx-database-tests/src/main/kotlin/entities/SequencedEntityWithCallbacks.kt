package entities

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.annotations.*
import com.onyx.persistence.annotations.values.IdentifierGenerator

/**
 * Created by Chris Osborn on 12/26/2014.
 */
@Entity
@Suppress("unused")
class SequencedEntityWithCallbacks : AbstractEntity(), IManagedEntity {

    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    var id: Long? = null

    @Attribute(size = 255)
    var name: String = ""

    @PreInsert
    fun beforeInsert() {
        name += "_PreInsert"
    }

    @PreUpdate
    fun beforeUpdate() {
        name += "_PreUpdate"
    }

    @PrePersist
    fun beforePersist() {
        name += "_PrePersist"
    }

    @PreRemove
    fun beforeRemove() {
        name += "_PreRemove"
    }

    @PostInsert
    fun afterInsert() {
        name += "_PostInsert"
    }

    @PostUpdate
    fun afterUpdate() {
        name += "_PostUpdate"
    }

    @PostPersist
    fun afterPersist() {
        name += "_PostPersist"
    }

    @PostRemove
    fun afterRemove() {
        name += "_PostRemove"
    }

}

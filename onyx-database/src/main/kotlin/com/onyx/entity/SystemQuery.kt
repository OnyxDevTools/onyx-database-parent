package com.onyx.entity

import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.Relationship
import com.onyx.persistence.annotations.values.CascadePolicy
import com.onyx.persistence.annotations.values.IdentifierGenerator
import com.onyx.persistence.annotations.values.RelationshipType

@Entity(fileName = "query.dat")
@Suppress("UNUSED")
class SystemQuery(

        @Identifier(generator = IdentifierGenerator.SEQUENCE)
        var queryId:Long = 0,

        @Attribute
        override var name:String = "",

        @Attribute
        var path:String = "",

        @Attribute
        var text:String = "",

        @Attribute
        var type: String = "",

        @Relationship(type = RelationshipType.MANY_TO_ONE,
                inverse = "queries",
                inverseClass = SystemDirectory::class,
                cascadePolicy = CascadePolicy.NONE)
        var directory: SystemDirectory? = null

) : ManagedEntity(), NamedEntity {

        val fileExtension:String
                get() =  ".${type.toLowerCase()}"
}

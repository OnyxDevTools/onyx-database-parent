package entities

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import java.util.*

@Entity(archiveDirectories = ["C:/Sandbox/Onyx/Tests/multistorage"], fileName = "allAttribute.dat")
open class MultiLocationEntity : AbstractEntity(), IManagedEntity {
    @Identifier
    @Attribute(size = 64)
    open var id: String? = null

    @Attribute(nullable = true)
    var longValue: Long? = null
    @Attribute
    var longPrimitive: Long = 0
    @Attribute
    var intValue: Int? = null
    @Attribute
    var intPrimitive: Int = 0
    @Attribute
    var stringValue: String? = null
    @Attribute
    var dateValue: Date? = null
    @Attribute
    var doubleValue: Double? = null
    @Attribute
    var doublePrimitive: Double = 0.toDouble()
    @Attribute
    var booleanValue: Boolean? = null
    @Attribute
    var booleanPrimitive: Boolean = false

}

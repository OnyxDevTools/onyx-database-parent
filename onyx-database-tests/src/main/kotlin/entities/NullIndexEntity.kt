package entities

import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.Index
import com.onyx.persistence.annotations.values.IdentifierGenerator

@Entity
class NullIndexEntity : ManagedEntity() {
    @Identifier(generator = IdentifierGenerator.UUID)
    var id: String = ""

    @Index
    var idx: String? = null

    @Index
    var longIndex: Long? = null

    @Index
    var intIndex: Long? = null

    @Index
    var doubleIndex: Double? = null

    @Index
    var floatIndex: Float? = null

    @Index
    var boolIndex: Boolean? = null

    @Index
    var shortIndex: Short? = null
}

package entities

import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.Index

/**
 * Created by Tim Osborn on 3/22/17.
 */
@Entity
class SelectIdentifierTestEntity : ManagedEntity() {

    @Identifier
    @Attribute
    var id: Long = 0

    @Attribute
    @Index
    var index: Int = 0

    @Attribute
    var attribute: String? = null
}

package entities

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier

/**
 * Created by timothy.osborn on 1/23/15.
 */
@Entity
class ValidationEntity : AbstractEntity(), IManagedEntity {
    @Attribute
    @Identifier
    var id: Long? = null

    @Attribute(nullable = false)
    var requiredString: String? = null

    @Attribute(size = 10)
    var maxSizeString: String? = null

}

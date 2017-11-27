package entities

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier

/**
 * Created by timothy.osborn on 2/10/15.
 */
@Entity
class ValidateRequiredIDEntity : AbstractEntity(), IManagedEntity {
    @Attribute
    @Identifier
    var id: String? = null

    @Attribute(nullable = false)
    var requiredString: String? = null

    @Attribute(size = 10)
    var maxSizeString: String? = null

}

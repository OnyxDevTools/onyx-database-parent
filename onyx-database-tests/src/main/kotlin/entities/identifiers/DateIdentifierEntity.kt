package entities.identifiers

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import entities.AbstractEntity

import java.util.Date

/**
 * Created by timothy.osborn on 11/3/14.
 */
@Entity
class DateIdentifierEntity : AbstractEntity(), IManagedEntity {
    @Attribute
    @Identifier
    var identifier: Date? = null

    @Attribute
    var correlation: Int = 0
}

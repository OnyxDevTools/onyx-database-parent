package entities

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.values.IdentifierGenerator

/**
 * Created by Chris Osborn on 12/26/2014.
 */
@Entity
class SimpleEntity : AbstractEntity(), IManagedEntity {

    @Identifier(generator = IdentifierGenerator.NONE)
    @Attribute(size = 255)
    var simpleId: String = ""

    @Attribute(size = 255)
    var name: String = ""

}

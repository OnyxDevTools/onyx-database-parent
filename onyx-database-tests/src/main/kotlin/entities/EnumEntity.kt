package entities

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.values.IdentifierGenerator
import pojo.SimpleEnum

/**
 * Created by Tim Osborn on 4/30/17.
 */
@Entity
class EnumEntity : AbstractEntity(), IManagedEntity {

    @Identifier(generator = IdentifierGenerator.NONE)
    @Attribute(size = 255)
    var simpleId: String = ""

    @Attribute(size = 255)
    var name: String = ""

    @Attribute
    var simpleEnum: SimpleEnum? = null

}


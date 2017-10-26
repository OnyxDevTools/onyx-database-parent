package entities.recreate


import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.values.IdentifierGenerator
import entities.AbstractEntity

/**
 * Created by timothy.osborn on 9/21/14.
 */
@com.onyx.persistence.annotations.Entity
class UserRoleTmp : AbstractEntity(), IManagedEntity {

    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    var id: Long? = null

    var type: TypeTmp? = null

}

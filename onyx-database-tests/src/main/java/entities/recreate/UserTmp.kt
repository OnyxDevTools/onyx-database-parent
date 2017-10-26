package entities.recreate

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.annotations.*
import com.onyx.persistence.annotations.values.CascadePolicy
import com.onyx.persistence.annotations.values.FetchPolicy
import com.onyx.persistence.annotations.values.IdentifierGenerator
import com.onyx.persistence.annotations.values.RelationshipType

import java.util.ArrayList
import java.util.Date

/**
 * Created by Tim Osborn on 8/30/14.
 */
@Entity
@Suppress("unused")
class UserTmp : IManagedEntity {

    @Attribute
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    var id: Long? = null

    @Attribute(size = 100)
    var firstName: String? = null

    @Relationship(fetchPolicy = FetchPolicy.EAGER, cascadePolicy = CascadePolicy.NONE, type = RelationshipType.MANY_TO_MANY, inverse = "users", inverseClass = AccountTmp::class)
    var accounts: MutableList<AccountTmp> = ArrayList()

    @Relationship(type = RelationshipType.ONE_TO_MANY, fetchPolicy = FetchPolicy.EAGER, cascadePolicy = CascadePolicy.SAVE, inverseClass = UserRoleTmp::class)
    var roles: MutableList<UserRoleTmp> = ArrayList()

    @Attribute
    var dateValue: Date? = null
}

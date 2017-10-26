package entities.recreate

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.annotations.*
import com.onyx.persistence.annotations.values.CascadePolicy
import com.onyx.persistence.annotations.values.FetchPolicy
import com.onyx.persistence.annotations.values.IdentifierGenerator
import com.onyx.persistence.annotations.values.RelationshipType
import entities.AbstractEntity

import java.util.ArrayList
import java.util.Date

/**
 * Created by timothy.osborn on 9/9/14.
 */
@Entity
@Suppress("unused")
class AccountTmp : AbstractEntity(), IManagedEntity {
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    var id: Long? = null

    @Attribute(size = 14)
    var phone: String? = null

    @Relationship(fetchPolicy = FetchPolicy.EAGER, cascadePolicy = CascadePolicy.SAVE, type = RelationshipType.ONE_TO_ONE, inverseClass = UserTmp::class)
    var primaryContact: UserTmp? = null

    @Relationship(fetchPolicy = FetchPolicy.EAGER, cascadePolicy = CascadePolicy.NONE, type = RelationshipType.MANY_TO_MANY, inverse = "accounts", inverseClass = UserTmp::class)
    var users: MutableList<UserTmp> = ArrayList()

    @Attribute
    var name: String? = null

    @Attribute
    var dateValue: Date? = null

}

package entities.exception

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.Index

/**
 * Created by timothy.osborn on 12/14/14.
 */
@Entity
class InvalidIndexTypeEntity : ManagedEntity(), IManagedEntity {
    @Attribute
    @Identifier
    var id = "asdf"

    @Index
    var index = true
}

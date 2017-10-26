package entities.exception

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity

/**
 * Created by timothy.osborn on 12/14/14.
 */
@Entity
class NoIdEntity : ManagedEntity(), IManagedEntity {

    @Attribute
    var attr: Long = 0
}

package entities.exception

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier

/**
 * Created by timothy.osborn on 12/14/14.
 */
@Entity
class InvalidIDEntity : ManagedEntity(), IManagedEntity {
    @Identifier
    var id: Double? = null
}

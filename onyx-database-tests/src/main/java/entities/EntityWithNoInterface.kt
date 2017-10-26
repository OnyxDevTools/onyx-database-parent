package entities

import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier

/**
 * Created by Tim Osborn on 9/2/15.
 */
@Entity
class EntityWithNoInterface : ManagedEntity() {
    @Identifier
    @Attribute
    var id: Int = 0
}

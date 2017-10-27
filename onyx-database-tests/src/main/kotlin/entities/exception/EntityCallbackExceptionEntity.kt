package entities.exception

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.PrePersist


/**
 * Created by timothy.osborn on 2/10/15.
 */
@Entity
class EntityCallbackExceptionEntity : ManagedEntity(), IManagedEntity {

    @Identifier
    var id: Long = 0

    @PrePersist
    @Suppress("unused")
    private fun onSomething():Nothing = throw Exception("HIYA")

}

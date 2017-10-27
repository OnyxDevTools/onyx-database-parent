package entities.exception

import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier

import java.io.Serializable

/**
 * Created by timothy.osborn on 12/14/14.
 */
@Entity
class EntityNoIPersistedEntity : Serializable {

    @Identifier
    var id: String? = null
}

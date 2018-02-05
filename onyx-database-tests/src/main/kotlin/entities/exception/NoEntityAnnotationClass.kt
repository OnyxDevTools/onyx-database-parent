package entities.exception

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Identifier

/**
 * Created by timothy.osborn on 12/14/14.
 */
class NoEntityAnnotationClass : IManagedEntity {

    override var referenceId: Long = 0L

    @Identifier
    @Attribute
    var id: String? = null
}

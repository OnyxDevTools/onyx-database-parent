package onyxdevtools.com.onyxandroidexample

import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.values.IdentifierGenerator

/**
 * Created by Tim Osborn on 3/29/17.
 */
@Entity
class MyEntity : ManagedEntity() {

    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    var id: Long = 0

    @Attribute
    var compare: Int = 0

    @Attribute
    var compareString: String? = null

    @Attribute
    var name: String? = null
}

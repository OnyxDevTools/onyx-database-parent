package entities.exception

import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.values.IdentifierGenerator

/**
 * Created by Tim Osborn on 8/25/16.
 */
@Entity
@Suppress("UNUSED")
class TestValidExtendAbstract : ValidAbstract() {
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    var myID: Int = 0

    @Attribute
    var myAttribute: Int = 0


}

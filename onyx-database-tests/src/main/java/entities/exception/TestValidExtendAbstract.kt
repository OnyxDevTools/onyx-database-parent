package entities.exception

import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.values.IdentifierGenerator

/**
 * Created by Tim Osborn on 8/25/16.
 */
@Entity
class TestValidExtendAbstract : ValidAbstract() {
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    protected var myID: Int = 0

    @Attribute
    protected var myAttribute: Int = 0


}

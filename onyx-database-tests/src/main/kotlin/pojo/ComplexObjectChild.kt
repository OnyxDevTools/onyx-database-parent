package pojo

import java.io.Serializable

/**
 * Created by timothy.osborn on 4/14/15.
 */
class ComplexObjectChild : Serializable {
    var parent: ComplexObject? = null

    var longValue: Long? = null
}

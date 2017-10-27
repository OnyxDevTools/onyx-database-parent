package pojo

import java.io.Serializable
import java.util.Date

/**
 * Created by timothy.osborn on 4/14/15.
 */
class ComplexObject : Serializable {
    var mine: ComplexObject? = null
    var child: ComplexObjectChild? = null

    var dateValue: Date? = null

}

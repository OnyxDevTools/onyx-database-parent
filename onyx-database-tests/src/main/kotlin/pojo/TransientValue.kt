package pojo

import java.io.Serializable
import java.util.Date

/**
 * Created by timothy.osborn on 4/14/15.
 */
class TransientValue : Serializable {

    var intValue: Int = 0

    @Transient
    var longValue: Long = 0

    var zDateValue: Date? = null
}

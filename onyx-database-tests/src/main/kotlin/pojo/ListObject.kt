package pojo

import java.io.Serializable

/**
 * Created by timothy.osborn on 4/14/15.
 */
class ListObject : Serializable {
    var objectArray: MutableList<Any>? = null
    var longArray: MutableList<Long>? = null
    var simpleArray: MutableList<Simple>? = null
}

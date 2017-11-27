package pojo

import java.io.Serializable

/**
 * Created by timothy.osborn on 4/14/15.
 */
class ArrayObject : Serializable {
    var objectArray: Array<Any?>? = null
    var longArray: Array<Long?>? = null
    var simpleArray: Array<Simple?>? = null
}

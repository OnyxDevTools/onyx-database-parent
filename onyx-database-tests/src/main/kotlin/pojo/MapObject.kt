package pojo

import java.io.Serializable

/**
 * Created by timothy.osborn on 4/14/15.
 */
class MapObject : Serializable {
    var simpleMap: MutableMap<String, Simple?>? = null
    var objectMap: MutableMap<Any, Any?>? = null
}

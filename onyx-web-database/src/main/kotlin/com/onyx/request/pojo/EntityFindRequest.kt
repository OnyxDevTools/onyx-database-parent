package com.onyx.request.pojo

import com.onyx.buffer.BufferStreamable

/**
 * Created by Tim Osborn on 8/30/14.
 *
 * POJO for entity find request
 */
class EntityFindRequest : BufferStreamable {
    var type: String? = null
    var id: Any? = null
    var partitionValue:String? = ""
}

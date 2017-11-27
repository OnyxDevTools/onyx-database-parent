package com.onyx.request.pojo

import com.onyx.buffer.BufferStreamable

/**
 * Created by timothy.osborn on 3/5/15.
 *
 * POJO for an entity relationship init body
 */
class EntityInitializeBody : BufferStreamable {
    var entityId: Any? = null
    var attribute: String? = null
    var entityType: String? = null
    var partitionId: Any? = null
}

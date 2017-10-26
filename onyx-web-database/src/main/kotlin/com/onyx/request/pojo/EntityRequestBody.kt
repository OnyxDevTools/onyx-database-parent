package com.onyx.request.pojo

import com.onyx.buffer.BufferStreamable

/**
 * Created by tosborn on 8/30/14.
 *
 * POJO for entity request
 */
class EntityRequestBody : BufferStreamable {
    var entity: Any? = null
    var type: String? = null
    var id: Any? = null
    var partitionId = ""
}

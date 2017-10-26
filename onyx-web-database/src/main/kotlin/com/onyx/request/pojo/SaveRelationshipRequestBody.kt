package com.onyx.request.pojo

import com.onyx.buffer.BufferStreamable

/**
 * Created by timothy.osborn on 4/8/15.
 *
 * Save Relationship Body
 */
class SaveRelationshipRequestBody : BufferStreamable {
    var type: String? = null
    var entity: Any? = null
    var relationship: String? = null
    var identifiers: Set<Any>? = null
}

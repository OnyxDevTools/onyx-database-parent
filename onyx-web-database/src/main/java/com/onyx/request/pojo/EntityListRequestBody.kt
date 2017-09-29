package com.onyx.request.pojo

import com.onyx.buffer.BufferStreamable

/**
 * Created by timothy.osborn on 4/8/15.
 *
 * Pojo for list request
 */
class EntityListRequestBody : BufferStreamable {

    var entities: String? = null
    var type: String? = null

}

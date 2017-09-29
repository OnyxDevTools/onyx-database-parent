package com.onyx.request.pojo

import com.onyx.buffer.BufferStreamable
import com.onyx.persistence.query.Query

/**
 * Created by timothy.osborn on 3/5/15.
 *
 * Pojo for execute query request
 */
class EntityQueryBody : BufferStreamable {
    var query: Query? = null
}

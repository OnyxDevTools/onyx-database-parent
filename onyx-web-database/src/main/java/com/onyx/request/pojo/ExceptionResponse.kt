package com.onyx.request.pojo

import com.onyx.buffer.BufferStreamable
import com.onyx.exception.OnyxException

/**
 * Created by timothy.osborn on 4/10/15.
 *
 * Response pojo for exception
 */
class ExceptionResponse @JvmOverloads constructor(val exception: OnyxException? = null, val exceptionType: String? = null) : BufferStreamable
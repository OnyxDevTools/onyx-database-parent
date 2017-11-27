package com.onyx.request.pojo

import com.onyx.buffer.BufferStreamable
import com.onyx.exception.OnyxException

/**
 * Created by timothy.osborn on 4/10/15.
 *
 * Response POJO for exception
 */
class ExceptionResponse @JvmOverloads constructor(val exception: OnyxException? = null, @Suppress("UNUSED") val exceptionType: String? = null) : BufferStreamable

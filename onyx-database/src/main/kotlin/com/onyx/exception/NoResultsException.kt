package com.onyx.exception

/**
 * Created by timothy.osborn on 11/30/14.
 *
 * Entity does not return results when expected
 */
class NoResultsException : OnyxException {

    constructor() : super()

    constructor(e: Throwable) : super(e)
}



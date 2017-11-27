package com.onyx.persistence.stream

/**
 * Created by Tim Osborn on 5/19/16.
 *
 * This is a custom lambda for the stream api.  It will indicate the expected
 * value is a map rather than hydrated entities
 */
interface QueryMapStream<in T : Map<String, Any?>> : QueryStream<T>

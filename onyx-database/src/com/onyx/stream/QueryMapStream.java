package com.onyx.stream;

/**
 * Created by tosborn1 on 5/19/16.
 *
 * This is a custom lambda for the stream api.  It will indicate the expected
 * value is a map rather than hydrated entities
 */
@FunctionalInterface
public interface QueryMapStream extends QueryStream {

}

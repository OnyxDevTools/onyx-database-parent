package com.onyx.persistence.query

/**
 * Created by Tim Osborn on 3/27/17.
 *
 * The possible event types of a record save
 */
enum class QueryListenerEvent {
    PRE_UPDATE, DELETE, INSERT, UPDATE
}

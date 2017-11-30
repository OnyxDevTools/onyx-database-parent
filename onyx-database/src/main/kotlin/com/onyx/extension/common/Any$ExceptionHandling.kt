package com.onyx.extension.common

/**
 * Method to help reduce boiler plate code for ignoring exceptions
 *
 * @since 2.0.0
 */
@Suppress("UNCHECKED_CAST")
inline fun <T : Any?> catchAll(body: () -> T):T = try {
        body()
    } catch (ignore:Exception) {
        null as T
}
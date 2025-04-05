package com.onyx.cloud.exceptions

/**
 * Custom exception typically used to indicate that a requested resource or entity was not found.
 *
 * @param message A detail message explaining the reason for the exception.
 * @param cause The underlying cause of this exception, if any.
 */
class NotFoundException(message: String?, cause: Throwable? = null) : Exception(message, cause)

package com.onyx.exception

/** Thrown when an insert encounters an existing identifier in the same partition. */
class EntityAlreadyExistsException @JvmOverloads constructor(message: String = "Entity already exists") : OnyxException(message)

package com.onyx.persistence

import java.io.Serializable

/**
 * All managed entities should implement this interface
 * Castable type for Managed Entity used within Managed Entities implementations.
 *
 * @author Chris Osborn
 * @since 1.0.0
 *
 * @see com.onyx.persistence.ManagedEntity
 *
 * @see com.onyx.persistence.manager.PersistenceManager
 */
interface IManagedEntity : Serializable
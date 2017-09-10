package com.onyx.entity

import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.PreInsert
import com.onyx.persistence.annotations.PreUpdate
import java.util.*

/**
 * Created by timothy.osborn on 3/15/15.
 *
 * Contains date modified information
 */
@Suppress("MemberVisibilityCanPrivate")
abstract class AbstractSystemEntity : ManagedEntity() {

    @Attribute
    var dateUpdated: Date? = null

    @Attribute
    var dateCreated: Date? = null

    @PreInsert
    @Suppress("UNUSED")
    private fun onPrePersist() {
        dateCreated = Date()
    }

    @PreUpdate
    @Suppress("UNUSED")
    private fun onPreUpdate() {
        dateUpdated = Date()
    }
}

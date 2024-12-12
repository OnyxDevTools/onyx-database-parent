@file:Suppress("unused")
package com.onyx.entity

import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.*
import com.onyx.persistence.annotations.values.IdentifierGenerator
import java.util.*

@Entity(fileName = "document")
data class Document(
    @Identifier(generator = IdentifierGenerator.UUID)
    var documentId: String = "",

    @Attribute
    var path: String = "",

    @Attribute
    var created: Date = Date(),

    @Attribute
    var updated: Date = Date(),

    @Attribute
    var mimeType: String = "",
) : ManagedEntity() {
    var content: String = ""
}

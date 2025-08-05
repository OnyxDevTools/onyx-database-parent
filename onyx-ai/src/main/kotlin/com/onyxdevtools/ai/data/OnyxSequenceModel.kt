package com.onyxdevtools.ai.data

import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import kotlin.random.Random

@Entity(fileName = "sequence")
data class SequenceEntry(
    @Identifier
    var id: Long = Random.nextLong(),
    @Attribute
    var data: MutableMap<String, Pair<DoubleArray, Array<DoubleArray>>> = hashMapOf(),
) : ManagedEntity()


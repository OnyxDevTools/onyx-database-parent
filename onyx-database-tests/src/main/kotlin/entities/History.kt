package entities

import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.*
import java.util.*


@Suppress("DEPRECATION")
@Entity(fileName = "history")
open class History(

    @Identifier
    val priceId: String = "",
    @Index
    val volume: Double = 0.0,
    @Partition
    val partition: String = "A",
    @Index
    val symbolId: String = "B",
    @Index
    val dateTime: Date = Date()
) : ManagedEntity()
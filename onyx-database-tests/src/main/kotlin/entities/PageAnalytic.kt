package entities

/**
 * Created by Tim Osborn on 4/22/17.
 */
import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.*
import com.onyx.persistence.annotations.values.IdentifierGenerator

import java.util.Calendar
import java.util.Date

/**
 * Created by Tim Osborn on 4/19/17.
 *
 * This object denotes a page request
 */
@Entity
@Suppress("unused")
class PageAnalytic : ManagedEntity() {

    @Attribute
    @Index(loadFactor = 2)
    var requestDate: Date? = null

    @Partition
    @Attribute
    var monthYear: String? = null

    init {
        val rightNow = Calendar.getInstance()
        this.requestDate = rightNow.time
        this.monthYear = rightNow.get(Calendar.YEAR).toString() + rightNow.get(Calendar.MONTH).toString()
    }

    @Attribute
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    var pageLoadId: Long = 0

    @Attribute
    var path: String? = null

    @Attribute
    var loadTime: Long = 0

    @Attribute
    var ipAddress: String? = null

    @Attribute
    var httpStatus: Int = 0

    @Attribute
    var agent: String? = null
}

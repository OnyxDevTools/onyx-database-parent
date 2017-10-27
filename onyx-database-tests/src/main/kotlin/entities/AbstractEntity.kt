package entities

import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.Attribute

import java.util.Date

/**
 * Created by timothy.osborn on 10/20/14.
 */
@Suppress("unused")
abstract class AbstractEntity : ManagedEntity() {

    @Attribute
    var dateCreated: Date? = null

    @Attribute
    var dateUpdated: Date? = null

    var doubleSample: Double = 0.toDouble()
    var dblSample: Double? = null

}

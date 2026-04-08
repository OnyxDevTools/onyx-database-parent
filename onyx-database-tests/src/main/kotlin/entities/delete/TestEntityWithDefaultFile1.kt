package entities.delete

import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier

/**
 * Entity using default file name - file should NOT be deleted if other entities use it
 */
@Entity
class TestEntityWithDefaultFile1 : ManagedEntity() {
    @Identifier
    var id: String? = null

    @Attribute
    var value: String? = null
}
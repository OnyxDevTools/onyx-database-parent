package entities.delete

import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier

/**
 * Another entity using default file name - shares file with TestEntityWithDefaultFile1
 */
@Entity
class TestEntityWithDefaultFile2 : ManagedEntity() {
    @Identifier
    var id: String? = null

    @Attribute
    var value: String? = null
}
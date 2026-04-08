package entities.delete

import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier

/**
 * Entity with custom file name - data file SHOULD be deleted when all records are deleted
 */
@Entity(fileName = "test_custom_file")
class TestEntityWithCustomFileName : ManagedEntity() {
    @Identifier
    var id: String? = null

    @Attribute
    var name: String? = null
}
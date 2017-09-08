package entities.exception;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.Attribute;
import com.onyx.persistence.annotations.Entity;
import com.onyx.persistence.annotations.Identifier;
import com.onyx.persistence.annotations.Partition;

/**
 * Created by timothy.osborn on 12/14/14.
 */
@Entity
public class MultiplePartitionAttributeEntity implements IManagedEntity {

    @Attribute
    @Identifier
    public String id;

    @Partition
    @Attribute
    public int partition1;

    @Partition
    @Attribute
    public int partition2;
}

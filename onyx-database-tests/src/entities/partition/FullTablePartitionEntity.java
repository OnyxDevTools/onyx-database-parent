package entities.partition;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;

/**
 * Created by timothy.osborn on 3/5/15.
 */

@Entity(fileName = "web/partition")
public class FullTablePartitionEntity extends ManagedEntity implements IManagedEntity
{
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    public Long id;

    @Attribute
    @Partition
    public Long partitionId;

    @Attribute
    public long indexVal;
}

package entities.partition;

/**
 * Created by timothy.osborn on 3/5/15.
 */

import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.*;

@Entity(fileName = "web/partition")
public class IndexPartitionEntity extends ManagedEntity implements IManagedEntity
{
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    public Long id;

    @Attribute
    @Partition
    public Long partitionId;

    @Index
    @Attribute
    public long indexVal;
}

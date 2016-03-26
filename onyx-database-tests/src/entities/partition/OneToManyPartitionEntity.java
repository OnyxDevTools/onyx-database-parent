package entities.partition;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;

/**
 * Created by timothy.osborn on 3/5/15.
 */
@Entity(fileName = "web/partition")
public class OneToManyPartitionEntity extends ManagedEntity implements IManagedEntity
{
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    public Long id;

    @Attribute
    @Partition
    public Long partitionId;

    @Relationship(type = RelationshipType.MANY_TO_ONE, inverse = "parents", inverseClass = ManyToOnePartitionEntity.class, cascadePolicy = CascadePolicy.SAVE)
    public ManyToOnePartitionEntity child;
}

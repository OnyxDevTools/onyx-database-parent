package entities.partition;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;

/**
 * Created by timothy.osborn on 3/5/15.
 */
@Entity
public class ToOnePartitionEntityParent extends ManagedEntity implements IManagedEntity
{

    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    public Long id;

    @Attribute
    @Partition
    public Long partitionId;

    @Relationship(type = RelationshipType.ONE_TO_ONE, inverse = "parent", inverseClass = ToOnePartitionEntityChild.class, cascadePolicy = CascadePolicy.DELETE)
    public ToOnePartitionEntityChild child;
}

package entities.partition;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;
import com.onyx.persistence.annotations.values.CascadePolicy;
import com.onyx.persistence.annotations.values.IdentifierGenerator;
import com.onyx.persistence.annotations.values.RelationshipType;

/**
 * Created by timothy.osborn on 3/5/15.
 */
@Entity(fileName = "web/partition")
public class ToOnePartitionEntityChild extends ManagedEntity implements IManagedEntity {

    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    public Long id;

    @Attribute
    @Partition
    public Long partitionId;

    @Relationship(type = RelationshipType.ONE_TO_ONE, inverse = "child", inverseClass = ToOnePartitionEntityParent.class, cascadePolicy = CascadePolicy.ALL)
    public ToOnePartitionEntityParent parent;
}
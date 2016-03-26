package entities.partition;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;

import java.util.List;

/**
 * Created by timothy.osborn on 3/5/15.
 */
@Entity(fileName = "web/partition")
public class ToManyPartitionEntityChild extends ManagedEntity implements IManagedEntity {

    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    public Long id;

    @Attribute
    @Partition
    public Long partitionId;

    @Relationship(type = RelationshipType.MANY_TO_MANY, inverse = "child", inverseClass = ToManyPartitionEntityParent.class, cascadePolicy = CascadePolicy.ALL, fetchPolicy = FetchPolicy.LAZY)
    public List<ToManyPartitionEntityParent> parent;

}

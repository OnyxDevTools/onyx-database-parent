package entities.partition;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;
import com.onyx.persistence.annotations.values.FetchPolicy;
import com.onyx.persistence.annotations.values.IdentifierGenerator;
import com.onyx.persistence.annotations.values.RelationshipType;

import java.util.List;

/**
 * Created by timothy.osborn on 3/5/15.
 */
@Entity(fileName = "web/partition")
public class ManyToOnePartitionEntity extends ManagedEntity implements IManagedEntity
{
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    public Long id;

    @Attribute
    @Partition
    public Long partitionId;

    @Relationship(type = RelationshipType.ONE_TO_MANY, inverse = "child", inverseClass = OneToManyPartitionEntity.class, fetchPolicy = FetchPolicy.EAGER)
    public List<OneToManyPartitionEntity> parents;
}

package entities.relationship;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.*;
import com.onyx.persistence.annotations.values.CascadePolicy;
import com.onyx.persistence.annotations.values.RelationshipType;
import entities.AbstractEntity;

import java.util.List;

/**
 * Created by timothy.osborn on 11/4/14.
 */
@Entity
public class HasInvalidToOneInverse extends AbstractEntity implements IManagedEntity
{
    @Attribute
    @Identifier
    public String identifier;

    @Attribute
    public int correlation;

    @Relationship(type = RelationshipType.ONE_TO_ONE,
            cascadePolicy = CascadePolicy.SAVE,
            inverseClass = HasInvalidToOne.class,
            inverse = "parent")
    public List<HasInvalidToOne> child;

}

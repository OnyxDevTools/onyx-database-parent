package entities.relationship;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.*;
import entities.AbstractEntity;

import java.util.List;

/**
 * Created by timothy.osborn on 11/4/14.
 */
@Entity
public class HasInvalidToOne extends AbstractEntity implements IManagedEntity
{
    @Attribute
    @Identifier
    public String identifier;

    @Attribute
    public int correlation;

    @Relationship(type = RelationshipType.ONE_TO_ONE,
            cascadePolicy = CascadePolicy.SAVE,
            inverseClass = OneToOneChild.class,
            inverse = "child")
    public HasInvalidToOneInverse parent;

}

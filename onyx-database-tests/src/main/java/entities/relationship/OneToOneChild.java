package entities.relationship;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.*;
import com.onyx.persistence.annotations.values.CascadePolicy;
import com.onyx.persistence.annotations.values.RelationshipType;
import entities.AbstractEntity;

/**
 * Created by timothy.osborn on 11/4/14.
 */
@Entity
public class OneToOneChild extends AbstractEntity implements IManagedEntity
{
    @Attribute
    @Identifier
    public String identifier;

    @Attribute
    public int correlation;

    @Relationship(type = RelationshipType.ONE_TO_ONE,
            cascadePolicy = CascadePolicy.NONE,
            inverseClass = OneToOneParent.class,
            inverse = "child")
    public OneToOneParent parent;

    @Relationship(type = RelationshipType.ONE_TO_ONE,
            cascadePolicy = CascadePolicy.NONE,
            inverseClass = OneToOneParent.class,
            inverse = "cascadeChild")
    public OneToOneParent cascadeParent;
}

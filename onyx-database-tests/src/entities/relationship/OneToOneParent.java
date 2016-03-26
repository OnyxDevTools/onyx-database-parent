package entities.relationship;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.*;
import entities.AbstractEntity;

/**
 * Created by timothy.osborn on 11/4/14.
 */
@Entity
public class OneToOneParent extends AbstractEntity implements IManagedEntity
{
    @Attribute
    @Identifier
    public String identifier;

    @Attribute
    public int correlation;

    @Relationship(type = RelationshipType.ONE_TO_ONE,
            cascadePolicy = CascadePolicy.SAVE,
            inverseClass = OneToOneChild.class,
            inverse = "parent")
    public OneToOneChild child;

    @Relationship(type = RelationshipType.ONE_TO_ONE,
            cascadePolicy = CascadePolicy.ALL,
            inverseClass = OneToOneChild.class,
            inverse = "cascadeParent")
    public OneToOneChild cascadeChild;


    @Relationship(type = RelationshipType.ONE_TO_ONE,
            cascadePolicy = CascadePolicy.ALL,
            inverseClass = OneToOneChild.class)
    public OneToOneChild childNoInverseCascade;

    @Relationship(type = RelationshipType.ONE_TO_ONE,
            cascadePolicy = CascadePolicy.SAVE,
            inverseClass = OneToOneChild.class)
    public OneToOneChild childNoInverse;
}

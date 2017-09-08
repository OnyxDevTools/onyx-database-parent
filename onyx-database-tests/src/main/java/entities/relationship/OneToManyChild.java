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
public class OneToManyChild extends AbstractEntity implements IManagedEntity
{
    @Attribute
    @Identifier
    public String identifier;

    @Attribute
    public int correlation;


    @Relationship(type = RelationshipType.MANY_TO_ONE,
            cascadePolicy = CascadePolicy.NONE,
            inverseClass = OneToManyParent.class,
            inverse = "childNoCascade")
    public OneToManyParent parentNoCascade;

    @Relationship(type = RelationshipType.MANY_TO_ONE,
            cascadePolicy = CascadePolicy.NONE,
            inverseClass = OneToManyParent.class,
            inverse = "childCascade")
    public OneToManyParent parentCascade;

    @Relationship(type = RelationshipType.MANY_TO_ONE,
            cascadePolicy = CascadePolicy.ALL,
            inverseClass = OneToManyParent.class,
            inverse = "childCascadeTwo")
    public OneToManyParent parentCascadeTwo;
    /*
    @Relationship(type = RelationshipType.MANY_TO_ONE,
            cascadePolicy = CascadePolicy.NONE,
            inverseClass = OneToManyParent.class,
            inverse = "childNoInverseCascade")
    public OneToManyParent parentNoInverseCascade;

    @Relationship(type = RelationshipType.MANY_TO_ONE,
            cascadePolicy = CascadePolicy.NONE,
            inverseClass = OneToManyParent.class,
            inverse = "childNoInverseNoCascade")
    public OneToManyParent parentNoInverseNoCascade;
    */
}

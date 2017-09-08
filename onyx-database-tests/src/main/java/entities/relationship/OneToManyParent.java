package entities.relationship;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.*;
import com.onyx.persistence.annotations.values.CascadePolicy;
import com.onyx.persistence.annotations.values.FetchPolicy;
import com.onyx.persistence.annotations.values.RelationshipType;
import entities.AbstractEntity;

import java.util.List;

/**
 * Created by timothy.osborn on 11/4/14.
 */
@Entity
public class OneToManyParent extends AbstractEntity implements IManagedEntity
{
    @Attribute
    @Identifier
    public String identifier;

    @Attribute
    public int correlation;


    @Relationship(type = RelationshipType.ONE_TO_MANY,
            cascadePolicy = CascadePolicy.NONE,
            inverseClass = OneToManyChild.class,
            inverse = "parentNoCascade")
    public List<OneToManyChild> childNoCascade;


    @Relationship(type = RelationshipType.ONE_TO_MANY,
            cascadePolicy = CascadePolicy.ALL,
            inverseClass = OneToManyChild.class,
            fetchPolicy = FetchPolicy.EAGER,
            inverse = "parentCascade")
    public List<OneToManyChild> childCascade;

    @Relationship(type = RelationshipType.ONE_TO_MANY,
            cascadePolicy = CascadePolicy.ALL,
            inverseClass = OneToManyChild.class)
    public List<OneToManyChild> childNoInverseCascade;

    @Relationship(type = RelationshipType.ONE_TO_MANY,
            cascadePolicy = CascadePolicy.NONE,
            inverseClass = OneToManyChild.class)
    public List<OneToManyChild> childNoInverseNoCascade;

    @Relationship(type = RelationshipType.ONE_TO_MANY,
            cascadePolicy = CascadePolicy.NONE,
            inverseClass = OneToManyChild.class,
            fetchPolicy = FetchPolicy.EAGER,
            inverse = "parentCascadeTwo")
    public List<OneToManyChild> childCascadeTwo;
}

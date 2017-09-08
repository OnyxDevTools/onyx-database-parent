package entities.relationship;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.*;
import com.onyx.persistence.annotations.values.CascadePolicy;
import com.onyx.persistence.annotations.values.FetchPolicy;
import com.onyx.persistence.annotations.values.RelationshipType;
import entities.AbstractEntity;

import java.util.List;

/**
 * Created by timothy.osborn on 11/5/14.
 */
@Entity
public class ManyToManyParent extends AbstractEntity implements IManagedEntity
{
    @Attribute
    @Identifier
    public String identifier;

    @Attribute
    public int correlation;

    @Relationship(type = RelationshipType.MANY_TO_MANY,
            cascadePolicy = CascadePolicy.NONE,
            fetchPolicy = FetchPolicy.EAGER,
            inverse = "parentNoCascade",
            inverseClass = ManyToManyChild.class)
    public List<ManyToManyChild> childNoCascade;

    @Relationship(type = RelationshipType.MANY_TO_MANY,
            cascadePolicy = CascadePolicy.ALL,
            fetchPolicy = FetchPolicy.LAZY,
            inverseClass = ManyToManyChild.class,
            inverse = "parentCascade")
    public List<ManyToManyChild> childCascade;

    @Relationship(type = RelationshipType.MANY_TO_MANY,
            cascadePolicy = CascadePolicy.ALL,
            fetchPolicy = FetchPolicy.LAZY,
            inverseClass = ManyToManyChild.class)
    public List<ManyToManyChild> childNoInverseCascade;


    @Relationship(type = RelationshipType.MANY_TO_MANY,
            cascadePolicy = CascadePolicy.SAVE,
            fetchPolicy = FetchPolicy.LAZY,
            inverseClass = ManyToManyChild.class,
            inverse = "parentCascade")
    public List<ManyToManyChild> childCascadeSave;

}

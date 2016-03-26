package entities.relationship;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.*;
import entities.AbstractEntity;

/**
 * Created by timothy.osborn on 1/26/15.
 */
@Entity
public class OneToOneRecursiveChild extends AbstractEntity implements IManagedEntity
{
    @Attribute
    @Identifier
    public long id;

    @Relationship(type = RelationshipType.ONE_TO_ONE, inverseClass = OneToOneRecursive.class, inverse = "child", cascadePolicy = CascadePolicy.SAVE)
    public OneToOneRecursive parent;

    @Relationship(type = RelationshipType.ONE_TO_ONE, inverseClass = OneToOneThreeDeep.class, inverse = "parent", cascadePolicy = CascadePolicy.SAVE)
    public OneToOneThreeDeep third;

}

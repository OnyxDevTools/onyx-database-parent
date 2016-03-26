package entities.relationship;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.*;
import entities.AbstractEntity;

/**
 * Created by timothy.osborn on 1/26/15.
 */
@Entity
public class OneToOneThreeDeep extends AbstractEntity implements IManagedEntity
{
    @Attribute
    @Identifier
    public long id;

    @Relationship(type = RelationshipType.ONE_TO_ONE, inverseClass = OneToOneRecursiveChild.class, inverse = "third")
    public OneToOneRecursiveChild parent;

}

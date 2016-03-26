package entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.*;

/**
 * Created by timothy.osborn on 11/15/14.
 */
@Entity
public class OneToOneChildFetchEntity extends AbstractInheritedAttributes implements IManagedEntity
{
    @Attribute
    @Identifier
    public String id;

    @Relationship(type = RelationshipType.ONE_TO_ONE, inverseClass = OneToOneFetchEntity.class, inverse = "child")
    public OneToOneFetchEntity parent;

}

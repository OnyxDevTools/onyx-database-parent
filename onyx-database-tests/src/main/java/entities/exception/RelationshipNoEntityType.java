package entities.exception;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.*;
import com.onyx.persistence.annotations.values.RelationshipType;

/**
 * Created by timothy.osborn on 12/14/14.
 */
@Entity
public class RelationshipNoEntityType implements IManagedEntity
{
    @Identifier
    @Attribute
    public String id = "234";

    @Relationship(type = RelationshipType.ONE_TO_MANY, inverseClass = Object.class)
    public Object relationship;
}

package entities.exception;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.*;
import entities.AllAttributeEntity;

/**
 * Created by timothy.osborn on 12/14/14.
 */
@Entity
public class NoInverseEntity implements IManagedEntity
{

    @Identifier
    @Attribute
    public String id = "234";

    @Relationship(type = RelationshipType.ONE_TO_ONE, inverse = "inversie", inverseClass = AllAttributeEntity.class)
    public AllAttributeEntity relationship;
}

package entities.exception;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;
import entities.AllAttributeEntity;
import entities.InheritedAttributeEntity;

/**
 * Created by timothy.osborn on 12/14/14.
 */
@Entity
public class EntityToOneDoesntMatch extends ManagedEntity implements IManagedEntity
{
    @Identifier
    @Attribute
    public String id = "234";

    @Relationship(type = RelationshipType.ONE_TO_ONE, inverseClass = AllAttributeEntity.class)
    public InheritedAttributeEntity relationship;
}

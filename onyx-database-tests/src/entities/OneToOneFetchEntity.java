package entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.*;

import java.util.List;

/**
 * Created by timothy.osborn on 11/15/14.
 */
@Entity
public class OneToOneFetchEntity extends AbstractInheritedAttributes implements IManagedEntity
{
    @Attribute
    @Identifier
    public String id;

    @Relationship(type = RelationshipType.ONE_TO_ONE, inverseClass = OneToOneChildFetchEntity.class, inverse = "parent")
    public OneToOneChildFetchEntity child;

    @Relationship(type = RelationshipType.ONE_TO_MANY, inverseClass = OneToManyChildFetchEntity.class, inverse = "parents")
    public List<OneToManyChildFetchEntity> children;
}

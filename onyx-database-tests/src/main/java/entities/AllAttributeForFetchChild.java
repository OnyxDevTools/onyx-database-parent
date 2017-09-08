package entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.*;
import com.onyx.persistence.annotations.values.IdentifierGenerator;
import com.onyx.persistence.annotations.values.RelationshipType;

/**
 * Created by timothy.osborn on 1/13/15.
 */
@Entity
public class AllAttributeForFetchChild extends AbstractEntity implements IManagedEntity
{
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    public Long id;

    @Attribute
    public String someOtherField;

    @Relationship(type = RelationshipType.ONE_TO_ONE, inverseClass = AllAttributeForFetch.class, inverse = "child")
    public AllAttributeForFetch parent;

}

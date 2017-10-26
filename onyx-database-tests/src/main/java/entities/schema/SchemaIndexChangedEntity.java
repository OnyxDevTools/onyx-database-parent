package entities.schema;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;
import com.onyx.persistence.annotations.values.IdentifierGenerator;

/**
 * Created by Tim Osborn on 9/1/15.
 */
@Entity
public class SchemaIndexChangedEntity extends ManagedEntity implements IManagedEntity
{
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    public long id;

    @Attribute(nullable=true)
    @Index
    public String longValue;

    @Attribute(nullable=true)
    @Index
    public int otherIndex;

}
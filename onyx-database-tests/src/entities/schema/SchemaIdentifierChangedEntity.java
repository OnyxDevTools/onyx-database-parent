package entities.schema;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.Attribute;
import com.onyx.persistence.annotations.Entity;
import com.onyx.persistence.annotations.Identifier;
import com.onyx.persistence.annotations.values.IdentifierGenerator;

/**
 * Created by tosborn1 on 8/26/15.
 */

@Entity
public class SchemaIdentifierChangedEntity extends ManagedEntity implements IManagedEntity
{
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    public long id;

    @Attribute(nullable=true)
    public Long longValue;
}
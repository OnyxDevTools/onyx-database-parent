package entities;

import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;
import com.onyx.persistence.annotations.values.CascadePolicy;
import com.onyx.persistence.annotations.values.IdentifierGenerator;
import com.onyx.persistence.annotations.values.RelationshipType;

/**
 * Created by Tim Osborn on 3/17/17.
 */
@Entity
public class PersonNoPartition extends ManagedEntity{
    public PersonNoPartition() {

    }
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    public Long id;

    @Attribute(nullable = false)
    public String firstName;

    @Attribute(nullable = false)
    public String lastName;

    @Relationship(type = RelationshipType.MANY_TO_ONE, inverseClass = AddressNoPartition.class, inverse = "occupants", cascadePolicy = CascadePolicy.ALL)
    public AddressNoPartition address;
}

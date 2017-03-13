package entities;

import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;

/**
 * Created by tosborn1 on 3/13/17.
 */
@Entity
public class Person extends ManagedEntity {

    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    public Long id;

    @Attribute(nullable = false)
    public String firstName;

    @Attribute(nullable = false)
    public String lastName;

    @Relationship(type = RelationshipType.MANY_TO_ONE, inverseClass = Address.class, inverse = "occupants", cascadePolicy = CascadePolicy.ALL)
    public Address address;

}

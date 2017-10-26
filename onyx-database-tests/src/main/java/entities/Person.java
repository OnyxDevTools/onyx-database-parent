package entities;

import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;
import com.onyx.persistence.annotations.values.CascadePolicy;
import com.onyx.persistence.annotations.values.IdentifierGenerator;
import com.onyx.persistence.annotations.values.RelationshipType;

/**
 * Created by Tim Osborn on 3/13/17.
 */
@Entity
public class Person extends ManagedEntity {

    public Person() {

    }
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    public Long id;

    @Partition
    @Attribute
    public String partitionVal = "ASDF";

    @Attribute(nullable = false)
    public String firstName;

    @Attribute(nullable = false)
    public String lastName;

    @Relationship(type = RelationshipType.MANY_TO_ONE, inverseClass = Address.class, inverse = "occupants", cascadePolicy = CascadePolicy.ALL)
    public Address address;

}

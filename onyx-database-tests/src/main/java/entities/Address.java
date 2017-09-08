package entities;

import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;
import com.onyx.persistence.annotations.values.CascadePolicy;
import com.onyx.persistence.annotations.values.FetchPolicy;
import com.onyx.persistence.annotations.values.IdentifierGenerator;
import com.onyx.persistence.annotations.values.RelationshipType;

import java.util.List;

/**
 * Created by tosborn1 on 3/13/17.
 */
@Entity
public class Address extends ManagedEntity
{
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    public Long id;

    @Attribute(nullable = false)
    public String street;

    @Attribute(nullable = false)
    public int houseNr;

    @Relationship(type = RelationshipType.ONE_TO_MANY, inverseClass = Person.class, inverse = "address", cascadePolicy = CascadePolicy.ALL, fetchPolicy = FetchPolicy.LAZY)
    public List<Person> occupants;
}

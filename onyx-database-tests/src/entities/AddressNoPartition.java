package entities;

import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;

import java.util.List;

/**
 * Created by tosborn1 on 3/17/17.
 */
@Entity
public class AddressNoPartition extends ManagedEntity
{
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    public Long id;

    @Attribute(nullable = false)
    public String street;

    @Attribute(nullable = false)
    public int houseNr;

    @Relationship(type = RelationshipType.ONE_TO_MANY, inverseClass = PersonNoPartition.class, inverse = "address", cascadePolicy = CascadePolicy.ALL, fetchPolicy = FetchPolicy.LAZY)
    public List<PersonNoPartition> occupants;
}

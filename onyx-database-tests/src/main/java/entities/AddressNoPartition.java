package entities;

import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;
import com.onyx.persistence.annotations.values.CascadePolicy;
import com.onyx.persistence.annotations.values.FetchPolicy;
import com.onyx.persistence.annotations.values.IdentifierGenerator;
import com.onyx.persistence.annotations.values.RelationshipType;

import java.util.List;

/**
 * Created by Tim Osborn on 3/17/17.
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

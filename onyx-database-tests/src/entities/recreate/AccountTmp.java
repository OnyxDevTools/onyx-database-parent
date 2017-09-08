package entities.recreate;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.*;
import com.onyx.persistence.annotations.values.CascadePolicy;
import com.onyx.persistence.annotations.values.FetchPolicy;
import com.onyx.persistence.annotations.values.IdentifierGenerator;
import com.onyx.persistence.annotations.values.RelationshipType;
import entities.AbstractEntity;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by timothy.osborn on 9/9/14.
 */
@Entity
public class AccountTmp extends AbstractEntity implements IManagedEntity
{
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    public Long id;

    @Attribute(size = 14)
    public String phone;

    @Relationship(fetchPolicy = FetchPolicy.EAGER, cascadePolicy = CascadePolicy.SAVE, type = RelationshipType.ONE_TO_ONE, inverseClass = UserTmp.class)
    public UserTmp primaryContact;

    @Relationship(fetchPolicy = FetchPolicy.EAGER, cascadePolicy = CascadePolicy.NONE, type = RelationshipType.MANY_TO_MANY, inverse = "accounts", inverseClass = UserTmp.class)
    public List<UserTmp> users = new ArrayList<UserTmp>();

    @Attribute
    public String name;

    @Attribute
    public Date dateValue;

}

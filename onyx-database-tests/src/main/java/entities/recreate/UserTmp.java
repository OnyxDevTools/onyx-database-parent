package entities.recreate;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.*;
import com.onyx.persistence.annotations.values.CascadePolicy;
import com.onyx.persistence.annotations.values.FetchPolicy;
import com.onyx.persistence.annotations.values.IdentifierGenerator;
import com.onyx.persistence.annotations.values.RelationshipType;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by tosborn on 8/30/14.
 */
@Entity
public class UserTmp implements IManagedEntity
{

    @Attribute
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    public Long id;

    @Attribute(size = 100)
    public String firstName;

    @Relationship(fetchPolicy = FetchPolicy.EAGER, cascadePolicy = CascadePolicy.NONE, type = RelationshipType.MANY_TO_MANY, inverse = "users", inverseClass = AccountTmp.class)
    public List<AccountTmp> accounts = new ArrayList<>();

    @Relationship(type = RelationshipType.ONE_TO_MANY, fetchPolicy = FetchPolicy.EAGER, cascadePolicy = CascadePolicy.SAVE, inverseClass = UserRoleTmp.class)
    public List<UserRoleTmp> roles = new ArrayList<>();

    @Attribute
    public Date dateValue;
}

package com.onyxdevtools.example.querying.entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.Attribute;
import com.onyx.persistence.annotations.CascadePolicy;
import com.onyx.persistence.annotations.Entity;
import com.onyx.persistence.annotations.FetchPolicy;
import com.onyx.persistence.annotations.Identifier;
import com.onyx.persistence.annotations.Relationship;
import com.onyx.persistence.annotations.RelationshipType;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author cosborn
 */
//J-
@Entity
public class Conference extends ManagedEntity implements IManagedEntity
{

    @Attribute
    @Identifier
    public String name;

    @Relationship(
            type = RelationshipType.ONE_TO_MANY,
            inverseClass = Division.class,
            inverse = "conference",
            cascadePolicy = CascadePolicy.ALL,
            fetchPolicy = FetchPolicy.EAGER
    )
    protected List<Division> divisions = new ArrayList<>();

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public List<Division> getDivisions()
    {
        return divisions;
    }

    public void setDivisions(List<Division> divisions)
    {
        this.divisions = divisions;
    }

}

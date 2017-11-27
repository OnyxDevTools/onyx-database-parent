package com.onyxdevtools.example.querying.entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;
import com.onyx.persistence.annotations.values.CascadePolicy;
import com.onyx.persistence.annotations.values.FetchPolicy;
import com.onyx.persistence.annotations.values.RelationshipType;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Chris Osborn
 */
//J-
@Entity
@SuppressWarnings("unused")
public class Conference extends ManagedEntity implements IManagedEntity
{

    @Attribute
    @Identifier
    private String name;

    @Relationship(
            type = RelationshipType.ONE_TO_MANY,
            inverseClass = Division.class,
            inverse = "conference",
            cascadePolicy = CascadePolicy.ALL,
            fetchPolicy = FetchPolicy.EAGER
    )
    private List<Division> divisions = new ArrayList<>();

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

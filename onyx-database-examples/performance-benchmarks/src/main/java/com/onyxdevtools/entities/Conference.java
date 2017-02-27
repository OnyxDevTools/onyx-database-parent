package com.onyxdevtools.entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;

import javax.persistence.CascadeType;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author cosborn
 */
//J-
@Entity
@javax.persistence.Entity
class Conference extends ManagedEntity implements IManagedEntity
{

    @Attribute
    @Identifier(loadFactor = 1)
    @Id
    private String conferenceName;

    @Relationship(
            type = RelationshipType.ONE_TO_MANY,
            inverseClass = Division.class,
            inverse = "conference",
            cascadePolicy = CascadePolicy.ALL,
            fetchPolicy = FetchPolicy.EAGER,
            loadFactor = 1
    )
    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL, targetEntity = Division.class)
    private List<Division> divisions = new ArrayList<>();

    @SuppressWarnings("unused")
    public String getConferenceName()
    {
        return conferenceName;
    }

    @SuppressWarnings("unused")
    public void setConferenceName(String conferenceName)
    {
        this.conferenceName = conferenceName;
    }

    @SuppressWarnings("unused")
    public List<Division> getDivisions()
    {
        return divisions;
    }

    @SuppressWarnings("unused")
    public void setDivisions(List<Division> divisions)
    {
        this.divisions = divisions;
    }

}

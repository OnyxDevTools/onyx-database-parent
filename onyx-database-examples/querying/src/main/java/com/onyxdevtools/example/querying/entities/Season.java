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

//J-
@Entity
public class Season extends ManagedEntity implements IManagedEntity
{

    @Attribute
    @Identifier
    protected int year;

    @Relationship(
            type = RelationshipType.ONE_TO_MANY,
            inverseClass = Conference.class,
            inverse = "league",
            cascadePolicy = CascadePolicy.ALL,
            fetchPolicy = FetchPolicy.EAGER
    )
    protected List<Conference> conferences = new ArrayList<>();

    public Season()
    {
    }

    public List<Conference> getConferences()
    {
        return conferences;
    }

    public void setConferences(List<Conference> conferences)
    {
        this.conferences = conferences;
    }

    public int getYear()
    {
        return year;
    }

    public void setYear(int year)
    {
        this.year = year;
    }

}

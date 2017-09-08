package com.onyxdevtools.example.querying.entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;
import com.onyx.persistence.annotations.values.CascadePolicy;
import com.onyx.persistence.annotations.values.FetchPolicy;
import com.onyx.persistence.annotations.values.RelationshipType;

import java.util.ArrayList;
import java.util.List;

//J-
@Entity
@SuppressWarnings("unused")
public class Season extends ManagedEntity implements IManagedEntity
{

    @Attribute
    @Identifier
    private int year;

    @Relationship(
            type = RelationshipType.ONE_TO_MANY,
            inverseClass = Conference.class,
            inverse = "league",
            cascadePolicy = CascadePolicy.ALL,
            fetchPolicy = FetchPolicy.EAGER
    )
    private List<Conference> conferences = new ArrayList<>();

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

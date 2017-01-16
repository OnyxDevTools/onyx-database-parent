package com.onyxdevtools.example.querying.entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;

import java.util.ArrayList;
import java.util.List;

//J-
@Entity
public class League extends ManagedEntity implements IManagedEntity
{

    @Attribute
    @Identifier
    protected String name;

    @Attribute
    protected String description;

    @Relationship(
            type = RelationshipType.ONE_TO_MANY,
            inverseClass = Season.class,
            cascadePolicy = CascadePolicy.ALL,
            fetchPolicy = FetchPolicy.LAZY
    )
    protected List<Season> seasons = new ArrayList<>();

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public List<Season> getSeasons()
    {
        return seasons;
    }

    public void setSeasons(List<Season> seasons)
    {
        this.seasons = seasons;
    }

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

}

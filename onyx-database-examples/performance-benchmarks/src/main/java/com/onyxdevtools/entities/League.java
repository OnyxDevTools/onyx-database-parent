package com.onyxdevtools.entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;
import com.onyx.persistence.annotations.Entity;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;

//J-
@Entity
@javax.persistence.Entity
public class League extends ManagedEntity implements IManagedEntity
{

    @Attribute
    @Identifier(loadFactor = 1)
    @Id
    private String name;

    @Attribute
    @Column
    private String description;

    @Relationship(
            type = RelationshipType.ONE_TO_MANY,
            inverseClass = Season.class,
            cascadePolicy = CascadePolicy.ALL,
            fetchPolicy = FetchPolicy.LAZY,
            loadFactor = 1
    )
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Season> seasons = new ArrayList<>();

    @SuppressWarnings("unused")
    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    @SuppressWarnings("unused")
    public List<Season> getSeasons()
    {
        return seasons;
    }

    @SuppressWarnings("unused")
    public void setSeasons(List<Season> seasons)
    {
        this.seasons = seasons;
    }

    @SuppressWarnings("unused")
    public String getDescription()
    {
        return description;
    }

    @SuppressWarnings("unused")
    public void setDescription(String description)
    {
        this.description = description;
    }

}

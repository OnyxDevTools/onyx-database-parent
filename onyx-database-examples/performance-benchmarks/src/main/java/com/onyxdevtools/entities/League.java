package com.onyxdevtools.entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.Attribute;
import com.onyx.persistence.annotations.CascadePolicy;
import com.onyx.persistence.annotations.Entity;
import com.onyx.persistence.annotations.FetchPolicy;
import com.onyx.persistence.annotations.Identifier;
import com.onyx.persistence.annotations.Relationship;
import com.onyx.persistence.annotations.RelationshipType;

import javax.persistence.*;
import java.util.ArrayList;

import java.util.List;

//J-
@Entity
@javax.persistence.Entity
public class League extends ManagedEntity implements IManagedEntity
{

    @Attribute
    @Identifier
    @Id
    protected String name;

    @Attribute
    @Column
    protected String description;

    @Relationship(
            type = RelationshipType.ONE_TO_MANY,
            inverseClass = Season.class,
            cascadePolicy = CascadePolicy.ALL,
            fetchPolicy = FetchPolicy.LAZY
    )
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    protected List<Season> seasons = new ArrayList();

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

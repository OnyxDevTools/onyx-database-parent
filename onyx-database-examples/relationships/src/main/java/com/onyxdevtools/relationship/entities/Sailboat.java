package com.onyxdevtools.relationship.entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;
import com.onyx.persistence.annotations.values.CascadePolicy;
import com.onyx.persistence.annotations.values.FetchPolicy;
import com.onyx.persistence.annotations.values.RelationshipType;

import java.util.List;

@Entity
@SuppressWarnings("unused")
public class Sailboat extends ManagedEntity implements IManagedEntity
{
    @Identifier
    @Attribute
    private String registrationCode;

    @Attribute
    private String name;

    @Relationship(type = RelationshipType.ONE_TO_MANY,
            inverseClass = CrewMember.class,
            inverse = "sailboat",
            cascadePolicy = CascadePolicy.ALL,
            fetchPolicy = FetchPolicy.LAZY)
    private List<CrewMember> crew;

    @Relationship(type = RelationshipType.ONE_TO_ONE,
            inverseClass = Skipper.class,
            inverse = "sailboat",
            cascadePolicy = CascadePolicy.ALL)
    private Skipper skipper;

    public String getRegistrationCode() {
        return registrationCode;
    }

    public void setRegistrationCode(String registrationCode) {
        this.registrationCode = registrationCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<CrewMember> getCrew() {
        return crew;
    }

    public void setCrew(List<CrewMember> crew) {
        this.crew = crew;
    }

    public Skipper getSkipper() {
        return skipper;
    }

    public void setSkipper(Skipper skipper) {
        this.skipper = skipper;
    }
}
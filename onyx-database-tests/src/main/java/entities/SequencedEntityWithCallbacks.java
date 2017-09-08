package entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.*;
import com.onyx.persistence.annotations.values.IdentifierGenerator;

/**
 * Created by cosborn on 12/26/2014.
 */
@Entity
public class SequencedEntityWithCallbacks extends AbstractEntity implements IManagedEntity {

    @Identifier(generator= IdentifierGenerator.SEQUENCE)
    @Attribute
    private Long id;

    @Attribute(size=255)
    protected String name;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @PreInsert
    public void beforeInsert(){
        setName(getName() + "_PreInsert");
    }

    @PreUpdate
    public void beforeUpdate(){
        setName(getName() + "_PreUpdate");
    }

    @PrePersist
    public void beforePersist(){
        setName(getName() + "_PrePersist");
    }

    @PreRemove
    public void beforeRemove(){
        setName(getName() + "_PreRemove");
    }

    @PostInsert
    public void afterInsert(){
        setName(getName() + "_PostInsert");
    }

    @PostUpdate
    public void afterUpdate(){
        setName(getName() + "_PostUpdate");
    }

    @PostPersist
    public void afterPersist(){
        setName(getName() + "_PostPersist");
    }

    @PostRemove
    public void afterRemove(){
        setName(getName() + "_PostRemove");
    }

}

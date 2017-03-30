package onyxdevtools.com.onyxandroidexample;

import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.Attribute;
import com.onyx.persistence.annotations.Entity;
import com.onyx.persistence.annotations.Identifier;
import com.onyx.persistence.annotations.IdentifierGenerator;

/**
 * Created by tosborn1 on 3/29/17.
 */
@Entity
public class MyEntity extends ManagedEntity {
    public MyEntity()
    {

    }

    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    private long id;

    @Attribute
    private int compare;

    @Attribute
    private String compareString;

    @Attribute
    private String name;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getCompare() {
        return compare;
    }

    public void setCompare(int compare) {
        this.compare = compare;
    }

    public String getCompareString() {
        return compareString;
    }

    public void setCompareString(String compareString) {
        this.compareString = compareString;
    }
}

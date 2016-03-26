package com.onyx.entity;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.Attribute;
import com.onyx.persistence.annotations.Identifier;
import com.onyx.persistence.annotations.IdentifierGenerator;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Created by timothy.osborn on 4/9/15.
 */

@com.onyx.persistence.annotations.Entity
@JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@uuid")
public class SystemError extends ManagedEntity implements IManagedEntity {

    public SystemError()
    {

    }

    public SystemError(Throwable e)
    {
        e.printStackTrace();
        exception = e;
        setMessage(exception.getMessage());

        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw);
        exception.printStackTrace(pw);

        setMessage(sw.toString());
        setType(exception.getClass().getCanonicalName());
    }

    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    protected Long id;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    @Attribute
    protected String packageClass;

    @Attribute
    protected String operation;

    @Attribute(size = 20000)
    protected String message;

    @Attribute
    protected String type;

    @JsonIgnore
    protected Throwable exception;

    public Throwable getException()
    {
        return exception;
    }

    public void setException(Throwable exception)
    {
        this.exception = exception;
    }

    public String getMessage()
    {
        return message;
    }

    public void setMessage(String message)
    {
        this.message = message;
    }

    public String getPackageClass()
    {
        return packageClass;
    }

    public void setPackageClass(String packageClass)
    {
        this.packageClass = packageClass;
    }

    public String getOperation()
    {
        return operation;
    }

    public void setOperation(String operation)
    {
        this.operation = operation;
    }

    public String getType()
    {
        return type;
    }

    public void setType(String type)
    {
        this.type = type;
    }
}

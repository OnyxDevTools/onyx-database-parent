package com.onyx.entity;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.Attribute;
import com.onyx.persistence.annotations.Identifier;
import com.onyx.persistence.annotations.IdentifierGenerator;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Created by timothy.osborn on 4/9/15.
 *
 * System error.  To be logged when logging gets to be implemented
 */
@com.onyx.persistence.annotations.Entity
public class SystemError extends ManagedEntity implements IManagedEntity {

    @SuppressWarnings("unused")
    public SystemError()
    {

    }

    @SuppressWarnings("unused")
    public SystemError(Throwable e)
    {
        e.printStackTrace();
        exception = e;
        setMessage(exception.getMessage());

        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw);
        exception.printStackTrace(pw);

        setMessage(sw.toString());
        setType(exception.getClass().getName());
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
    private String packageClass;

    @Attribute
    private String operation;

    @Attribute(size = 20000)
    protected String message;

    @Attribute
    protected String type;

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

    @SuppressWarnings("unused")
    public String getPackageClass()
    {
        return packageClass;
    }

    @SuppressWarnings("unused")
    public void setPackageClass(String packageClass)
    {
        this.packageClass = packageClass;
    }

    @SuppressWarnings("unused")
    public String getOperation()
    {
        return operation;
    }

    @SuppressWarnings("unused")
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

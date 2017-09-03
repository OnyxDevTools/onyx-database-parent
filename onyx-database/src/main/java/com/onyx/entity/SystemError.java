package com.onyx.entity;

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
public class SystemError extends AbstractSystemEntity {

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

    @SuppressWarnings("WeakerAccess")
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    protected Long id;

    @SuppressWarnings("unused")
    public Long getId()
    {
        return id;
    }

    @SuppressWarnings("unused")
    public void setId(Long id)
    {
        this.id = id;
    }

    @Attribute
    private String packageClass;

    @Attribute
    private String operation;

    @SuppressWarnings("WeakerAccess")
    @Attribute(size = 20000)
    protected String message;

    @SuppressWarnings("WeakerAccess")
    @Attribute
    protected String type;

    @SuppressWarnings("WeakerAccess")
    protected Throwable exception;

    @SuppressWarnings("unused")
    public Throwable getException()
    {
        return exception;
    }

    @SuppressWarnings("unused")
    public void setException(Throwable exception)
    {
        this.exception = exception;
    }

    @SuppressWarnings("unused")
    public String getMessage()
    {
        return message;
    }

    @SuppressWarnings("WeakerAccess")
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

    @SuppressWarnings("unused")
    public String getType()
    {
        return type;
    }

    @SuppressWarnings("WeakerAccess")
    public void setType(String type)
    {
        this.type = type;
    }
}

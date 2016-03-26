package com.onyx.request.pojo;

import java.util.concurrent.TimeUnit;

/**
 * Created by timothy.osborn on 5/28/15.
 */
public class LockPayload
{
    protected String name;
    protected TimeUnit unit;
    protected long timeout;

    public LockPayload(String name, TimeUnit unit, long timeout)
    {
        this.name = name;
        this.unit = unit;
        this.timeout = timeout;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public TimeUnit getUnit() {
        return unit;
    }

    public void setUnit(TimeUnit unit) {
        this.unit = unit;
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }
}

package com.onyxdevtools.entity;

import io.swagger.client.model.ManagedEntity;

@SuppressWarnings("unused")
public class Job extends ManagedEntity
{

    private int jobId;
    private String description;
    private Person employee;

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Person getEmployee() {
        return employee;
    }

    public void setEmployee(Person employee) {
        this.employee = employee;
    }

    public int getJobId() {
        return jobId;
    }

    public void setJobId(int jobId) {
        this.jobId = jobId;
    }
}
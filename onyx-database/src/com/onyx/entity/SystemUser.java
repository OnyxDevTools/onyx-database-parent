package com.onyx.entity;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.Attribute;
import com.onyx.persistence.annotations.Entity;
import com.onyx.persistence.annotations.Identifier;

import javax.security.auth.Subject;
import java.security.Principal;

/**
 * Created by cosbor11 on 3/2/2015.
 */
@Entity
public class SystemUser extends AbstractSystemEntity implements IManagedEntity, Principal
{

    public SystemUser()
    {

    }

    @Identifier(loadFactor = 1)
    @Attribute
    protected String username;

    @Attribute(size = 255)
    protected String password;

    @Attribute
    protected int roleOrdinal;

    protected SystemUserRole role;

    public String getId() {
        return username;
    }

    public void setId(String id) {
        this.username = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }


    public int getRoleOrdinal()
    {
        return roleOrdinal;
    }

    public void setRoleOrdinal(int roleOrdinal)
    {
        this.roleOrdinal = roleOrdinal;
    }

    public SystemUserRole getRole()
    {
        role = SystemUserRole.values()[roleOrdinal];
        return role;
    }

    public void setRole(SystemUserRole role)
    {
        this.role = role;
        this.roleOrdinal = role.ordinal();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SystemUser that = (SystemUser) o;

        if (username != null ? !username.equals(that.username) : that.username != null) return false;
        return password != null ? password.equals(that.password) : that.password == null;

    }

    @Override
    public int hashCode() {
        int result = username != null ? username.hashCode() : 0;
        result = 31 * result + (password != null ? password.hashCode() : 0);
        return result;
    }

    @Override
    public String getName()
    {
        return username;
    }

    @Override
    public boolean implies(Subject subject)
    {
        return false;
    }
}
package com.onyx.entity;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.Attribute;
import com.onyx.persistence.annotations.Entity;
import com.onyx.persistence.annotations.Identifier;

import javax.security.auth.Subject;
import java.security.Principal;

/**
 * Created by cosbor11 on 3/2/2015.
 *
 * User for the database
 */
@Entity
public class SystemUser extends AbstractSystemEntity implements IManagedEntity, Principal
{

    public SystemUser()
    {

    }

    @Identifier(loadFactor = 3)
    @Attribute
    protected String username;

    @Attribute(size = 255)
    protected String password;

    @Attribute
    private int roleOrdinal;

    private SystemUserRole role;

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

    @SuppressWarnings("unused")
    public int getRoleOrdinal()
    {
        return roleOrdinal;
    }

    @SuppressWarnings("unused")
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

        return username != null ? username.equals(that.username) : that.username == null
                && (password != null ? password.equals(that.password) : that.password == null);

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
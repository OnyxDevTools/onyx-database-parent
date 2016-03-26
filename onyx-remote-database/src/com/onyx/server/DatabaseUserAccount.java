package com.onyx.server;

import com.onyx.entity.SystemUser;
import io.undertow.security.idm.Account;

import java.security.Principal;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by timothy.osborn on 4/23/15.
 */
public class DatabaseUserAccount implements Account
{
    private final SystemUser user;
    private Set<String> roles = null;

    public DatabaseUserAccount(SystemUser user)
    {
        this.user = user;
    }

    @Override
    public final Principal getPrincipal()
    {
        return user;
    }

    @Override
    public final Set<String> getRoles()
    {
        if(user == null)
        {
            return null;
        }
        if(roles == null)
        {
            roles = new HashSet<>();
            if(user.getRole() != null)
            {
                roles.add(user.getRole().name());
            }
        }
        return roles;
    }

    public final String getPassword()
    {
        return user.getPassword();
    }
}

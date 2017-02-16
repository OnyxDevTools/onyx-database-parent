package com.onyxdevtools.relationship;

import com.onyx.exception.EntityException;

public class Main
{

    public static void main(String[] args) throws EntityException
    {
        OneToOneExample.demo();
        OneToManyExample.demo();
        ManyToManyExample.demo();

        CascadeDeferExample.demo();
        CascadeAllExample.demo();
        CascadeSaveExample.demo();

        FetchPolicyExample.demo();
    }
}

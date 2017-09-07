package com.onyxdevtools.relationship;

import com.onyx.exception.OnyxException;

public class Main
{

    public static void main(String[] args) throws OnyxException
    {
        OneToOneExample.demo();
        OneToManyExample.demo();
        ManyToManyExample.demo();

        CascadeDeferExample.demo();
        CascadeAllExample.demo();
        CascadeSaveExample.demo();

        FetchPolicyExample.demo();

        System.exit(0);
    }
}

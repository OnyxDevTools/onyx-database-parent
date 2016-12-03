package com.onyxdevtools.relationship;

import java.io.IOException;

public class Main
{

    public static void main(String[] args) throws IOException
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

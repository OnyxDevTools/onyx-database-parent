package com.onyxdevtools.persist;

import com.onyx.exception.OnyxException;

@SuppressWarnings("WeakerAccess")
public class Main
{

    public static void main(String[] args) throws OnyxException
    {

        SavingAnEntityExample.main(args);
        BatchSavingDataExample.main(args);
        
        DeletingAnEntityExample.main(args);
        BatchDeletingDataExample.main(args);

    }
}

package com.onyxdevtools.persist;

import com.onyx.exception.OnyxException;

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

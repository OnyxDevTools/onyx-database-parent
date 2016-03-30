package com.onyxdevtools.quickstart;

import com.onyx.exception.EntityException;
import com.onyx.exception.InitializationException;
import java.io.IOException;

public class Main
{

    public static void main(String[] args) throws InitializationException, EntityException, IOException
    {

        SavingAnEntityExample.main(args);
        BatchSavingDataExample.main(args);
        
        DeletingAnEntityExample.main(args);
        BatchDeletingDataExample.main(args);

    }
}

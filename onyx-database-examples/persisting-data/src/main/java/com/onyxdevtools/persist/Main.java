package com.onyxdevtools.persist;

import java.io.IOException;

public class Main
{

    public static void main(String[] args) throws IOException
    {

        SavingAnEntityExample.main(args);
        BatchSavingDataExample.main(args);
        
        DeletingAnEntityExample.main(args);
        BatchDeletingDataExample.main(args);

    }
}

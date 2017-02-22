package com.onyxdevtools.lifecycle;

abstract class AbstractDemo {

    /**
     * Helper method to assert a boolean
     * @param message Message to show if assert fails
     * @param operator boolean to assert
     */
    static void assertTrue(String message, boolean operator)
    {
        if(!operator)
        {
            System.err.println(message);
        }
    }
}

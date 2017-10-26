package com.onyxdevtools.index;

/**
 @author  Chris Osborn
 */
abstract class AbstractDemo
{

    /**
     * Helper method to verify 2 objects have the same key.
     *
     * @param  message      Message to display if the assert fails
     * @param  comparison1  First object to compare
     * @param  comparison2  Second object to compare against
     */
    static void assertEquals(@SuppressWarnings("SameParameterValue") final String message, final Object comparison1, @SuppressWarnings("SameParameterValue") final Object comparison2)
    {
        if (comparison1 == comparison2)
        {
            return;
        }

        if (comparison1.equals(comparison2))
        {
            return;
        }

        System.err.println(message);
    }
}

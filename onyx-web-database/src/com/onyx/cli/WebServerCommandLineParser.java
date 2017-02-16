package com.onyx.cli;

import com.onyx.application.DatabaseServer;
import com.onyx.application.WebDatabaseServer;
import com.onyx.server.cli.CommandLineParser;
import org.apache.commons.cli.*;

/**
 * Created by tosborn1 on 2/13/17.
 *
 * This is the overridden parser specific to the Web Server
 */
public class WebServerCommandLineParser extends CommandLineParser {

    // Command Line Options
    private static final String OPTION_WEBSERVICE_PORT = "web-port";

    /**
     * Parse command line arguments
     * @param args arguments to parse
     * @return CommandLine object
     */
    protected CommandLine parseCommandLine(String[] args)
    {
        CommandLine commandLine = super.parseCommandLine(args);

        if(!commandLine.hasOption(OPTION_WEBSERVICE_PORT)
                && commandLine.hasOption(OPTION_PORT))
        {
            System.err.println("Invalid Port, you must specify a WebService Port.");
            throw new RuntimeException();
        }

        return commandLine;
    }

    /**
     * Configure Database With Command Line Options.  With V1.2.0, we removed the useSSL since it is redundant.
     *
     * @param databaseServer - Database Server instance
     * @param args    - Command line arguments
     * @since 1.0.0
     */
    public void configureDatabaseWithCommandLineOptions(DatabaseServer databaseServer, String[] args) {
        CommandLine commandLine = parseCommandLine(args);
        super.configureDatabaseWithCommandLineOptions(databaseServer, args);

        if (commandLine.hasOption(OPTION_WEBSERVICE_PORT)) {
            ((WebDatabaseServer)databaseServer).setWebServicePort(Integer.valueOf(commandLine.getOptionValue(OPTION_WEBSERVICE_PORT)));
        }
    }
}

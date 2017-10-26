package com.onyx.cli

import com.onyx.application.impl.DatabaseServer
import com.onyx.application.impl.WebDatabaseServer
import org.apache.commons.cli.*

/**
 * Created by Tim Osborn on 2/13/17.
 *
 * This is the overridden parser specific to the Web Server
 */
class WebServerCommandLineParser(args: Array<String>) : CommandLineParser(args) {

    /**
     * Parse command line arguments
     * @param args arguments to parse
     * @return CommandLine object
     */
    override fun parseCommandLine(args: Array<String>): CommandLine {
        val commandLine = super.parseCommandLine(args)

        if (!commandLine.hasOption(OPTION_WEBSERVICE_PORT) && commandLine.hasOption(CommandLineParser.OPTION_PORT)) {
            System.err.println("Invalid Port, you must specify a WebService Port.")
            throw RuntimeException()
        }

        return commandLine
    }

    /**
     * Configure Database With Command Line Options.  With V1.2.0, we removed the useSSL since it is redundant.
     *
     * @param databaseServer - Database Server instance
     * @since 1.0.0
     */
    override fun configureDatabaseWithCommandLineOptions(databaseServer: DatabaseServer) {
        super.configureDatabaseWithCommandLineOptions(databaseServer)
        if (commandLineArguments.hasOption(OPTION_WEBSERVICE_PORT))
            (databaseServer as WebDatabaseServer).webServicePort = Integer.valueOf(commandLineArguments.getOptionValue(OPTION_WEBSERVICE_PORT))
    }

    companion object {
        private val OPTION_WEBSERVICE_PORT = "web-port"
    }
}

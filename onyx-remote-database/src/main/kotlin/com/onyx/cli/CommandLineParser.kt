package com.onyx.cli

import com.onyx.application.impl.DatabaseServer
import org.apache.commons.cli.*

/**
 * Created by tosborn1 on 2/13/17.
 *
 * This is responsible for parsing the command line options of a database server
 */
open class CommandLineParser {

    /**
     * Configure Command Line Options for Data Server
     *
     * @return CLI Options
     * @since 1.0.0
     */
    private fun configureCommandLineOptions(): Options {
        // create the Options
        val options = Options()

        options.addOption("P", OPTION_PORT, true, "Server port number")
        options.addOption("u", OPTION_USER, true, "Database username")
        options.addOption("p", OPTION_PASSWORD, true, "Database password")
        options.addOption("l", OPTION_LOCATION, true, "Database filesystem location")
        options.addOption("i", OPTION_INSTANCE, true, "Database instance name")
        options.addOption("k", OPTION_KEYSTORE, true, "Keystore file path.")
        options.addOption("t", OPTION_TRUST_STORE, true, "Trust Store file path.")
        options.addOption("sp", OPTION_STORE_PASSWORD, true, "SSL Store Password.")
        options.addOption("kp", OPTION_KEYSTORE_PASSWORD, true, "Keystore password. ")
        options.addOption("tp", OPTION_TRUST_STORE_PASSWORD, true, "Trust Store password.")

        options.addOption("h", OPTION_HELP, false, "Help")

        return options
    }

    /**
     * Parse command line arguments
     * @param args arguments to parse
     * @return CommandLine object
     */
    protected open fun parseCommandLine(args: Array<String>): CommandLine {
        // create the command line parser
        val parser = DefaultParser()
        val options = configureCommandLineOptions()

        val commandLine: CommandLine
        try {
            // parse the command line arguments
            commandLine = parser.parse(options, args)
        } catch (exp: ParseException) {
            // oops, something went wrong
            System.err.println("Invalid Arguments.  Reason: " + exp.message)
            throw RuntimeException(exp)
        }

        if (commandLine.hasOption(OPTION_HELP)) {
            val formatter = HelpFormatter()
            formatter.printHelp("onyx", options)
            System.exit(0)
        }

        if (!commandLine.hasOption(OPTION_LOCATION)) {
            System.err.println("Invalid Database Location.  Location is required!")
            throw RuntimeException()
        }

        return commandLine
    }

    /**
     * Base command line builder with all the default options
     */
    fun buildDatabaseWithCommandLineOptions(databaseServer: DatabaseServer, commandLine:CommandLine) {
        if (commandLine.hasOption(OPTION_USER) && commandLine.hasOption(OPTION_PASSWORD))
            databaseServer.setCredentials(commandLine.getOptionValue(OPTION_USER), commandLine.getOptionValue(OPTION_PASSWORD))
        else
            databaseServer.setCredentials("admin", "admin")
        if (commandLine.hasOption(OPTION_PORT))
            databaseServer.port = Integer.valueOf(commandLine.getOptionValue(OPTION_PORT))
        if (commandLine.hasOption(OPTION_INSTANCE))
            databaseServer.instance = commandLine.getOptionValue(OPTION_INSTANCE)
        if (commandLine.hasOption(OPTION_KEYSTORE))
            databaseServer.sslKeystoreFilePath = commandLine.getOptionValue(OPTION_KEYSTORE)
        if (commandLine.hasOption(OPTION_TRUST_STORE))
            databaseServer.sslTrustStoreFilePath = commandLine.getOptionValue(OPTION_TRUST_STORE)
        if (commandLine.hasOption(OPTION_KEYSTORE_PASSWORD))
            databaseServer.sslKeystorePassword = commandLine.getOptionValue(OPTION_KEYSTORE_PASSWORD)
        if (commandLine.hasOption(OPTION_STORE_PASSWORD))
            databaseServer.sslStorePassword = commandLine.getOptionValue(OPTION_STORE_PASSWORD)
        if (commandLine.hasOption(OPTION_TRUST_STORE_PASSWORD))
            databaseServer.sslTrustStorePassword = commandLine.getOptionValue(OPTION_TRUST_STORE_PASSWORD)
    }

    /**
     * Configure Database With Command Line Options.  With V1.2.0, we removed the useSSL since it is redundant.
     *
     * @param args    - Command line arguments
     * @since 1.0.0
     */
    open fun buildDatabaseWithCommandLineOptions(args: Array<String>):DatabaseServer {
        val commandLine = parseCommandLine(args)
        val databaseServer = DatabaseServer(commandLine.getOptionValue(OPTION_LOCATION))
        buildDatabaseWithCommandLineOptions(databaseServer, commandLine)
        return databaseServer
    }

    companion object {

        // Command Line Options
        val OPTION_PORT = "port"
        val OPTION_USER = "user"
        val OPTION_PASSWORD = "password"
        val OPTION_LOCATION = "location"
        val OPTION_INSTANCE = "instance"
        val OPTION_KEYSTORE = "keystore"
        val OPTION_TRUST_STORE = "trust-store"
        val OPTION_STORE_PASSWORD = "store-password"
        val OPTION_KEYSTORE_PASSWORD = "keystore-password"
        private val OPTION_TRUST_STORE_PASSWORD = "trust-password"
        private val OPTION_HELP = "help"
    }
}

package com.onyx.server.cli;

import com.onyx.application.DatabaseServer;
import org.apache.commons.cli.*;

/**
 * Created by tosborn1 on 2/13/17.
 *
 * This is responsible for parsing the command line options of a database server
 */
public class CommandLineParser {

    // Command Line Options
    protected static final String OPTION_PORT = "port";
    private static final String OPTION_USER = "user";
    private static final String OPTION_PASSWORD = "password";
    private static final String OPTION_LOCATION = "location";
    private static final String OPTION_INSTANCE = "instance";
    private static final String OPTION_KEYSTORE = "keystore";
    private static final String OPTION_TRUST_STORE = "trust-store";
    private static final String OPTION_STORE_PASSWORD = "store-password";
    private static final String OPTION_KEYSTORE_PASSWORD = "keystore-password";
    private static final String OPTION_TRUST_STORE_PASSWORD = "trust-password";
    private static final String OPTION_MAX_WORKER_THREADS = "max-threads";
    private static final String OPTION_HELP = "help";

    /**
     * Configure Command Line Options for Data Server
     *
     * @return CLI Options
     * @since 1.0.0
     */
    private Options configureCommandLineOptions() {
        // create the Options
        Options options = new Options();

        options.addOption("P", OPTION_PORT, true, "Server port number");
        options.addOption("u", OPTION_USER, true, "Database username");
        options.addOption("p", OPTION_PASSWORD, true, "Database password");
        options.addOption("l", OPTION_LOCATION, true, "Database filesystem location");
        options.addOption("i", OPTION_INSTANCE, true, "Database instance name");
        options.addOption("k", OPTION_KEYSTORE, true, "Keystore file path.");
        options.addOption("t", OPTION_TRUST_STORE, true, "Trust Store file path.");
        options.addOption("sp", OPTION_STORE_PASSWORD, true, "SSL Store Password.");
        options.addOption("kp", OPTION_KEYSTORE_PASSWORD, true, "Keystore password. ");
        options.addOption("tp", OPTION_TRUST_STORE_PASSWORD, true, "Trust Store password.");

        options.addOption("h", OPTION_HELP, false, "Help");

        return options;
    }

    /**
     * Parse command line arguments
     * @param args arguments to parse
     * @return CommandLine object
     */
    protected CommandLine parseCommandLine(String[] args)
    {
        // create the command line parser
        org.apache.commons.cli.CommandLineParser parser = new DefaultParser();
        Options options = configureCommandLineOptions();

        CommandLine commandLine;
        try {
            // parse the command line arguments
            commandLine = parser.parse(options, args);
        } catch (ParseException exp) {
            // oops, something went wrong
            System.err.println("Invalid Arguments.  Reason: " + exp.getMessage());
            throw new RuntimeException(exp);
        }

        if (commandLine.hasOption(OPTION_HELP)) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("onyx", options);
            System.exit(0);
        }

        if (!commandLine.hasOption(OPTION_LOCATION)) {
            System.err.println("Invalid Database Location.  Location is required!");
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
        if (commandLine.hasOption(OPTION_PORT)) {
            databaseServer.setPort(Integer.valueOf(commandLine.getOptionValue(OPTION_PORT)));
        }

        if (commandLine.hasOption(OPTION_USER)
                && commandLine.hasOption(OPTION_PASSWORD)) {
            databaseServer.setCredentials(commandLine.getOptionValue(OPTION_USER), commandLine.getOptionValue(OPTION_PASSWORD));
        } else {
            databaseServer.setCredentials("admin", "admin");
        }

        databaseServer.setDatabaseLocation(commandLine.getOptionValue(OPTION_LOCATION));

        if (commandLine.hasOption(OPTION_INSTANCE)) {
            databaseServer.setInstance(commandLine.getOptionValue(OPTION_INSTANCE));
        }

        if (commandLine.hasOption(OPTION_KEYSTORE)) {
            databaseServer.setSslKeystoreFilePath(commandLine.getOptionValue(OPTION_KEYSTORE));
        }

        if (commandLine.hasOption(OPTION_TRUST_STORE)) {
            databaseServer.setSslTrustStoreFilePath(commandLine.getOptionValue(OPTION_TRUST_STORE));
        }

        if (commandLine.hasOption(OPTION_KEYSTORE_PASSWORD)) {
            databaseServer.setSslKeystorePassword(commandLine.getOptionValue(OPTION_KEYSTORE_PASSWORD));
        }

        if (commandLine.hasOption(OPTION_STORE_PASSWORD)) {
            databaseServer.setSslStorePassword(commandLine.getOptionValue(OPTION_STORE_PASSWORD));
        }

        if (commandLine.hasOption(OPTION_TRUST_STORE_PASSWORD)) {
            databaseServer.setSslTrustStorePassword(commandLine.getOptionValue(OPTION_TRUST_STORE_PASSWORD));
        }

        if (commandLine.hasOption(OPTION_MAX_WORKER_THREADS)) {
            databaseServer.setMaxWorkerThreads(Integer.parseInt(commandLine.getOptionValue(OPTION_MAX_WORKER_THREADS)));
        }
    }
}

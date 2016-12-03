package com.onyx.persistence.factory.impl;

import com.onyx.exception.SingletonException;
import com.onyx.persistence.context.impl.DefaultSchemaContext;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.exception.InitializationException;
import com.onyx.persistence.manager.impl.EmbeddedPersistenceManager;
import com.onyx.util.EncryptionUtil;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Persistence manager factory for an embedded Java based database.
 *
 * This is responsible for configuring a database that does persist to disk and is not accessible to external API or Network calls.
 *
 * @author Tim Osborn
 * @since 1.0.0
 *
 * <pre>
 * <code>
 *
 *   PersistenceManagerFactory factory = new EmbeddedPersistenceManagerFactory();
 *   factory.setCredentials("username", "password");
 *   factory.setLocation("/MyDatabaseLocation")
 *   factory.initialize();
 *
 *   PersistenceManager manager = factory.getPersistenceManager();
 *
 *   factory.close(); //Close the in embedded database
 *
 *   or ...
 *
 *   PersistenceManagerFactory factory = new EmbeddedPersistenceManagerFactory();
 *   factory.setCredentials("username", "password");
 *   factory.setLocation("/MyDatabaseLocation")
 *   factory.setContext(new EmbeddedSchemaContext());
 *   factory.initialize();
 *
 *   PersistenceManager manager = factory.getPersistenceManager();
 *
 *   factory.close(); //Close the in memory database
 *
 * </code>
 * </pre>
 *
 * @see com.onyx.persistence.factory.PersistenceManagerFactory
 */
public class EmbeddedPersistenceManagerFactory implements PersistenceManagerFactory {

    private static final String CREDENTIALS_FILE = "tmp";

    protected String location;
    protected SchemaContext context;
    protected String user = "admin";
    protected String password = "admin";

    public static final String DEFAULT_INSTANCE = "ONYX_DATABASE";

    protected String instance = DEFAULT_INSTANCE;

    // Enable history journaling ot keep a transaction history
    protected boolean enableJournaling = false;

    /**
     * Overridden constructor to include SchemaContext
     *
     * @since 1.0.0
     * @param instance Instance of context to determine how to store and structure data
     */
    public EmbeddedPersistenceManagerFactory(String instance)
    {
        super();
        this.instance = instance;


    }

    /**
     * Constructor that ensures safe shutdown
     * @since 1.0.0
     */
    public EmbeddedPersistenceManagerFactory()
    {
        this(DEFAULT_INSTANCE);

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                close();
            }
        });
    }

    /**
     * Set Database Location.  A directory on local file system
     *
     * @since 1.0.0
     * @param location Database Local Location
     */
    @Override
    public void setDatabaseLocation(String location)
    {
        this.location = location;
        if(context != null)
            context.setLocation(location);
    }

    /**
     * Get Database Location
     *
     * @since 1.0.0
     * @return Local database location on disk
     */
    @Override
    public String getDatabaseLocation()
    {
        return location;
    }

    /**
     * Set Schema Context.  Schema context determines how the data is structured and what mechanism for data storage is used
     * If this is not specified, it will instantiate a DefaultSchemaContext
     *
     * @since 1.0.0
     *
     * @see com.onyx.persistence.context.impl.DefaultSchemaContext
     *
     * @param context Schema Context implementation
     */
    @Override
    public void setSchemaContext(SchemaContext context)
    {
        this.context = context;
        DefaultSchemaContext.registeredSchemaContexts.put(this.instance, context);
        if(location != null)
        {
            this.context.setLocation(location);
        }
    }

    /**
     * Get Schema Context
     *
     * @since 1.0.0
     * @return Schema Context
     */
    @Override
    public SchemaContext getSchemaContext()
    {
        return context;
    }

    /**
     * Set Credentials. Set username and password
     *
     * @since 1.0.0
     * @param user Set username
     * @param password Set Password
     */
    public final void setCredentials(String user, String password)
    {
        this.user = user;
        this.password = password;
    }

    private String creds = null;

    /**
     * Get Credentials formatted for HTTP Basic Authentication to be inserted into Cookie
     * @since 1.0.0
     *
     * @return Formatted Credentials
     */
    @Override
    public final String getCredentials()
    {
        if(creds == null)
        {
            try {
                creds = this.user + ":" + EncryptionUtil.encrypt(this.password);
            } catch (Exception ex) {
                Logger.getLogger(EmbeddedPersistenceManagerFactory.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return creds;
    }

    /**
     * Initialize the database connection and storage mechanisms
     *
     * @since 1.0.0
     * @throws InitializationException Failure to start database due to either invalid credentials or a lock on the database already exists.
     */
    @Override
    public void initialize() throws InitializationException
    {
        try
        {
            if(context == null)
            {
                context = new DefaultSchemaContext(location);
                context.setLocation(location);
            }

            // Ensure the database file exists
            final File databaseDirectory = new File(this.location);

            if (!databaseDirectory.exists())
            {
                databaseDirectory.mkdirs();
                createCredentialsFile();
            }

            acquireLock();

            if (!databaseDirectory.canWrite())
            {
                releaseLock();
                throw new InitializationException(InitializationException.DATABASE_FILE_PERMISSION_ERROR);
            }

            if (!checkCredentials())
            {
                releaseLock();
                throw new InitializationException(InitializationException.INVALID_CREDENTIALS);
            }

            this.getPersistenceManager();
            context.start();
        }
        catch (OverlappingFileLockException e)
        {
            releaseLock();
            throw new InitializationException(InitializationException.DATABASE_LOCKED);
        }
        catch (IOException e)
        {
            releaseLock();
            throw new InitializationException(InitializationException.UNKNOWN_EXCEPTION, e);
        }
    }

    private FileChannel fileChannelLock;

    private java.nio.channels.FileLock lock;

    /**
     * Acquire Database Lock
     *
     * @since 1.0.0
     * @throws IOException
     */
    private void acquireLock() throws IOException
    {
        final File lockFile = new File(this.location + "/lock");

        if(!lockFile.exists())
        {
            lockFile.createNewFile();
        }

        fileChannelLock = new RandomAccessFile(lockFile, "rw").getChannel();

        lock = fileChannelLock.tryLock();
    }

    /**
     * Release Database Lock
     *
     * @since 1.0.0
     */
    private void releaseLock()
    {
        try
        {
            if(lock != null)
            {
                try
                {
                    lock.release();
                }
                finally
                {
                    fileChannelLock.close();
                }
            }
        } catch (Exception e){
        }
    }

    /**
     * Safe shutdown of database
     * @since 1.0.0
     * @throws java.io.IOException Cannot flush file changes
     * @throws com.onyx.exception.SingletonException Highlander, there can be only one
     */
    @Override
    public void close()
    {
        try {
            context.shutdown();
        } catch (SingletonException ignore) {}
        releaseLock();
    }

    /**
     * Check to see if credentials in the database match configuration
     *
     * @since 1.0.0
     * @return Indicator to see if the factory's credentials are valid
     */
    private final boolean checkCredentials() throws InitializationException
    {
        final File databaseFile = new File(location);
        if (!databaseFile.exists())
        {
            return true;
        }
        try
        {
            // Read the credentials and compare
            File credFile = new File(location + File.separator + CREDENTIALS_FILE);
            String credentials = new String(Files.readAllBytes(Paths.get(credFile.getAbsolutePath())), StandardCharsets.UTF_16);
            return credentials.equals(encryptCredentials());

        } catch (InitializationException e)
        {
            throw new InitializationException(InitializationException.UNKNOWN_EXCEPTION, e);
        }
        catch (IOException e)
        {
            throw new InitializationException(InitializationException.UNKNOWN_EXCEPTION, e);
        }

    }
    
     /**
      * Encrypt Credentials
      *
      * @since 1.0.0
      * @return Encrypted Credentials
      */
    private final String encryptCredentials() throws InitializationException
    {
        try
        {
            return EncryptionUtil.encrypt(user + password);
        } catch (Exception e)  {
            throw new InitializationException(InitializationException.UNKNOWN_EXCEPTION, e);
        }
    }

    /**
     * Create Credentials File
     *
     * @throws InitializationException Cannot create or write to credentials file
     * @since 1.0.0
     */
    private final void createCredentialsFile() throws InitializationException
    {
        FileOutputStream fileStream = null;
        try
        {
            //create a temporary file
            final File credentialsFile = new File(location + File.separator + CREDENTIALS_FILE);
            credentialsFile.getParentFile().mkdirs();
            credentialsFile.createNewFile();
            fileStream = new FileOutputStream(credentialsFile);
            fileStream.write(encryptCredentials().getBytes(StandardCharsets.UTF_16));
        } catch (InitializationException e)
        {
            throw new InitializationException(InitializationException.UNKNOWN_EXCEPTION, e);
        }
        catch (IOException e)
        {
            throw new InitializationException(InitializationException.UNKNOWN_EXCEPTION, e);
        } finally
        {
            try
            {
                // Close the fileStream regardless of what happens...
                if(fileStream != null)
                {
                    fileStream.close();
                }
            } catch (Exception ignore)
            {
            }
        }
    }

    protected PersistenceManager persistenceManager = null;

    /**
     * Getter for persistence manager
     *
     * @since 1.0.0
     * @return Instantiated Persistence Manager
     */
    @Override
    public PersistenceManager getPersistenceManager()
    {
        if(persistenceManager == null)
        {
            try {
                this.persistenceManager = new EmbeddedPersistenceManager();
                ((EmbeddedPersistenceManager)this.persistenceManager).setJournalingEnabled(this.enableJournaling);
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
            this.persistenceManager.setContext(context);
        }
        return persistenceManager;
    }

    /**
     * Getter for journaling enabled.
     *
     * @return Whether journaling is enabled or disabled
     */
    public boolean isEnableJournaling() {
        return enableJournaling;
    }

    /**
     * Set journaling enabled.  If enabled, this will create WAL transaction files
     * Note:  If enabled this will add overhead for persisting data.
     *
     * @param enableJournaling True or False
     */
    public void setEnableJournaling(boolean enableJournaling) {
        this.enableJournaling = enableJournaling;
    }

    /**
     * Ignore for embedded factory.  This does not have relevance.
     * @param socketPort
     */
    public void setSocketPort(int socketPort) {}

    /**
     * This method parses the location to get the host name
     * @return Parsed host name in format onyx://(hostname):port
     */
    protected String getHostName()
    {
        String registryEndpoint = this.location.replace("onx://", "").replace("ws://", "").replace("wss://", "");
        registryEndpoint = registryEndpoint.replaceFirst(":\\d+", "");
        return registryEndpoint;
    }

}

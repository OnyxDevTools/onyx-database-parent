package com.onyx.client;

import com.onyx.exception.EntityException;
import com.onyx.exception.QueryException;
import com.onyx.map.serializer.ObjectBuffer;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.request.pojo.RequestEndpoint;
import com.onyx.request.pojo.RequestPriority;
import com.onyx.request.pojo.RequestToken;
import com.onyx.request.pojo.RequestTokenType;

import javax.websocket.*;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;


/**
 * Created by timothy.osborn on 4/25/15.
 */
@ClientEndpoint
public class DefaultDatabaseEndpoint {

    protected Map<Short, RequestToken> requests = Collections.synchronizedMap(new HashMap<Short, RequestToken>());
    protected Map<Short, RequestToken> responses = Collections.synchronizedMap(new HashMap());

    protected ExecutorService socketThreadPool = Executors.newCachedThreadPool();

    // Connection timeout to write a message
    public static final int WRITE_TIMEOUT = 60;

    // Download File Buffer size
    public static final int FILE_DOWNLOAD_BUFFER_SIZE = 4096; // 4k

    // Query Timeout
    protected int queryTimeout = 240;

    protected SchemaContext context;

    protected String fileServiceEndpoint;

    @SuppressWarnings("unused")
    private Session session;

    /**
     * Constructor
     */
    public DefaultDatabaseEndpoint() {
    }

    /**
     * Constructor
     *
     * @param queryTimeout
     */
    public DefaultDatabaseEndpoint(int queryTimeout, SchemaContext context) {
        this.queryTimeout = queryTimeout;
        this.context = context;
        this.fileServiceEndpoint = this.context.getRemoteFileBase();
    }

    /**
     * Constructor with file Service Endpoint
     *
     * @param queryTimeout
     * @param fileServiceEndpoint
     */
    public DefaultDatabaseEndpoint(int queryTimeout, String fileServiceEndpoint)
    {
        this.queryTimeout = queryTimeout;
        this.fileServiceEndpoint = fileServiceEndpoint;
    }

    /**
     * Getter and setter for session
     * @return
     */
    public Session getSession() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    /**
     * On close session
     *
     * @param session
     */
    @OnClose
    public void onClose(javax.websocket.Session session) {
        this.session = null;
    }

    /**
     * On Connect
     *
     * @param session
     */
    @OnOpen
    public void onConnect(Session session) {
        this.session = session;
    }

    /**
     * Receive Message
     *
     * @param data
     * @param last
     * @param session
     */
    @OnMessage
    public void onMessage(byte[] data, boolean last, Session session) {
        try {
            if (last) {
                ByteBuffer buffer = ObjectBuffer.allocate(data.length);
                buffer.put(data);
                buffer.rewind();

                while (buffer.position() < buffer.limit()) {
                    final RequestToken token = RequestToken.getToken(buffer);

                    final RequestToken localToken = requests.remove(token.getMessageId());
                    responses.put(token.getMessageId(), token);

                    if (localToken != null && localToken.getListener() != null)
                        localToken.getListener().getCountDownLatch().countDown();

                }
            }
        } catch (IOException e) {
            // TODO: Log exception
        }

    }

    /**
     * Download file from file server
     * @param path
     * @param to
     * @return
     * @throws QueryException
     */
    public File download(Path path, Path to) throws QueryException
    {
        InputStream is = null;
        FileOutputStream fos = null;

        this.fileServiceEndpoint = this.fileServiceEndpoint.replaceFirst("ws://", "http://");

        try
        {
            final URL url= new URL(this.fileServiceEndpoint + path.toString());
            final URLConnection urlConn = url.openConnection();//connect
            urlConn.setConnectTimeout(queryTimeout);

            new File(to.toFile().getParent()).mkdirs();
            to.toFile().createNewFile();

            is = urlConn.getInputStream();             //get connection inputstream
            fos = new FileOutputStream(to.toFile());   //open outputstream to local file

            final byte[] buffer = new byte[FILE_DOWNLOAD_BUFFER_SIZE];              //declare 4KB buffer
            int len;

            //while we have available data, continue downloading and storing to local file
            while ((len = is.read(buffer)) > 0)
            {
                fos.write(buffer, 0, len);
            }

            return to.toFile();
        }
        catch (FileNotFoundException e)
        {
            return null;
        }
        catch (Exception e)
        {
            throw new QueryException(QueryException.CONNECTION_EXCEPTION);
        }finally
        {
            try {
                if (is != null) {
                    try {
                        is.close();
                    } catch (IOException e) { }
                }
            } finally {
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (IOException e) { }
                }
            }
        }

    }

    /**
     * Execute with timeout
     *
     * @param token
     * @return
     * @throws EntityException
     */
    public Object execute(RequestToken token) throws EntityException {
        return this.execute(token, RequestPriority.CRITICAL);
    }

    /**
     * Execute with timeout
     *
     * @param token
     * @return
     * @throws EntityException
     */
    public Object execute(RequestToken token, RequestPriority priority) throws EntityException {
        int  timeout = queryTimeout / (priority.ordinal() + 1);
        return this.execute(token, timeout);
    }

    /**
     * Send Message
     *
     * @param token
     */
    public Object execute(RequestToken token, int timeout) throws EntityException {
        if (session == null || !session.isOpen())
            throw new QueryException(QueryException.CONNECTION_EXCEPTION);

        ByteBuffer buffer = null;

        try {
            buffer = RequestToken.getPacket(token);
        } catch (IOException e) {
            throw new QueryException(QueryException.SERIALIZATION_EXCEPTION, e);
        }

        if (session == null || !session.isOpen()) {
            throw new QueryException(QueryException.CONNECTION_EXCEPTION);
        }

        final MessageListener listener = new MessageListener(token, timeout);
        requests.put(token.getMessageId(), token);

        final Future<Void> future = session.getAsyncRemote().sendBinary(buffer);

        try {
            future.get(WRITE_TIMEOUT, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new QueryException(e);
        } catch (ExecutionException e) {
            throw new QueryException(QueryException.CONNECTION_EXCEPTION, e);
        } catch (TimeoutException e) {
            throw new QueryException(QueryException.QUERY_TIMEOUT, e);
        }

        final Future response = socketThreadPool.submit(listener);

        try {
            response.get(timeout, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new QueryException(QueryException.CONNECTION_EXCEPTION, e);
        } catch (ExecutionException e) {
            throw new QueryException(QueryException.CONNECTION_EXCEPTION, e);
        } catch (TimeoutException e) {
            throw new QueryException(QueryException.QUERY_TIMEOUT);
        }

        // Check to see if there is an exception
        if (listener.getResults() instanceof EntityException)
            throw (EntityException) listener.getResults();
        else if (listener.getResults() instanceof Exception)
            throw new RuntimeException((Exception) listener.getResults());

        return listener.getResults();
    }

    /**
     * Class that is used to poll for results
     */
    public class MessageListener implements Runnable {

        protected RequestToken token = null;
        protected CountDownLatch countDownLatch = new CountDownLatch(1);
        protected int timeout;

        /**
         * Message Listener Constructor
         *
         * @param token
         */
        public MessageListener(RequestToken token, int timeout) {
            this.token = token;
            this.token.setListener(this);
            this.timeout = timeout;
        }

        /**
         * Run Message Listener
         */
        @Override
        public void run() {

            try {
                countDownLatch.await(timeout, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                short messageId = token.getMessageId();
                this.token = new RequestToken(RequestEndpoint.values()[token.getEndpoint()], RequestTokenType.values()[token.getType()], new QueryException(QueryException.QUERY_TIMEOUT));
                this.token.setMessageId(messageId);
                responses.remove(token.getMessageId());
                requests.remove(token.getMessageId());
                return;
            }

            if(countDownLatch.getCount() > 0)
            {
                short messageId = token.getMessageId();
                this.token = new RequestToken(RequestEndpoint.values()[token.getEndpoint()], RequestTokenType.values()[token.getType()], new QueryException(QueryException.QUERY_TIMEOUT));
                this.token.setMessageId(messageId);
                responses.remove(token.getMessageId());
                requests.remove(token.getMessageId());
                return;
            }

            RequestToken tmpToken = responses.remove(token.getMessageId());

            if (tmpToken != null)
                this.token = tmpToken;
        }

        /**
         * Get Results
         *
         * @return
         */
        public Object getResults() {
            if (this.token == null) {
                token.setPayload(new QueryException(QueryException.QUERY_TIMEOUT));
            }
            return this.token.getPayload();
        }

        /**
         * Get Countdown latch for completion
         *
         * @return
         */
        public CountDownLatch getCountDownLatch() {
            return countDownLatch;
        }
    }

}

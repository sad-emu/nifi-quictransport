package transport.nifi.processors.quictransport;

import kwik.core.src.main.java.tech.kwik.core.QuicConnection;
import kwik.core.src.main.java.tech.kwik.core.QuicStream;
import kwik.core.src.main.java.tech.kwik.core.log.Logger;
import kwik.core.src.main.java.tech.kwik.core.log.NullLogger;
import kwik.core.src.main.java.tech.kwik.core.log.SysOutLogger;
import kwik.core.src.main.java.tech.kwik.core.server.ApplicationProtocolConnection;
import kwik.core.src.main.java.tech.kwik.core.server.ApplicationProtocolConnectionFactory;
import kwik.core.src.main.java.tech.kwik.core.server.ServerConnectionConfig;
import kwik.core.src.main.java.tech.kwik.core.server.ServerConnector;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.flowfile.attributes.CoreAttributes;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessSessionFactory;
import org.apache.nifi.processor.io.OutputStreamCallback;

import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.atomic.AtomicReference;

public class QuicServer {

    private String certificatePath = null;
    private String keyPath = null;
    private int connectionPort = -1;
    private  int maxStreams = -1;
    private String protocolName;
    private boolean logPackets = false;
    private ServerConnector serverConnector = null;
    private AtomicReference<ProcessSessionFactory> factoryRef = null;
    private org.slf4j.Logger nifiLogger = null;

    public QuicServer(String certificatePath, String keyPath, int connectionPort, String protocolName,
                      boolean logPackets, org.slf4j.Logger logger, int maxStreams) {
        this.certificatePath = certificatePath;
        this.keyPath = keyPath;
        this.connectionPort = connectionPort;
        this.protocolName = protocolName;
        this.logPackets = logPackets;
        this.nifiLogger = logger;
        this.maxStreams = maxStreams;
    }

    public void setFactoryRef(AtomicReference<ProcessSessionFactory> factoryRef){
        this.factoryRef = factoryRef;
    }

    public void init() throws Exception {

        Logger log = new NullLogger();
        log.timeFormat(Logger.TimeFormat.Long);
        log.logWarning(false);
        log.logInfo(false);

        ServerConnectionConfig serverConnectionConfig = ServerConnectionConfig.builder()
                .maxOpenPeerInitiatedBidirectionalStreams(this.maxStreams)  // Mandatory setting to maximize concurrent streams on a connection.
                .build();

        this.serverConnector = ServerConnector.builder()
                .withPort(connectionPort)
                .withCertificate(new FileInputStream(certificatePath), new FileInputStream(keyPath))
                .withConfiguration(serverConnectionConfig)
                .withLogger(log)
                .build();

        registerProtocolHandler(serverConnector, log, this.protocolName, this.factoryRef, nifiLogger);

        this.serverConnector.start();

        log.info("Started echo server on port " + connectionPort);
    }

    private static void registerProtocolHandler(ServerConnector serverConnector, Logger log, String protocolName,
                                                AtomicReference<ProcessSessionFactory> factoryRef,
                                                org.slf4j.Logger logger) {
        serverConnector.registerApplicationProtocol(protocolName,
                new EchoProtocolConnectionFactory(log, factoryRef, logger));
    }

    public void stop(){
        this.serverConnector.close();
    }

    /**
     * The factory that creates the (echo) application protocol connection.
     */
    static class EchoProtocolConnectionFactory implements ApplicationProtocolConnectionFactory {
        private final Logger log;
        private final org.slf4j.Logger nifiLogger;
        private AtomicReference<ProcessSessionFactory> factoryRef = null;

        public EchoProtocolConnectionFactory(Logger log, AtomicReference<ProcessSessionFactory> factoryRef,
                                             org.slf4j.Logger logger) {
            this.log = log;
            this.factoryRef = factoryRef;
            this.nifiLogger = logger;
        }

        @Override
        public ApplicationProtocolConnection createConnection(String protocol, QuicConnection quicConnection) {
            return new EchoProtocolConnection(quicConnection, log, factoryRef, nifiLogger);
        }

        @Override
        public int maxConcurrentPeerInitiatedUnidirectionalStreams() {
            return 0;  // Because unidirectional streams are not used
        }

        @Override
        public int maxConcurrentPeerInitiatedBidirectionalStreams() {
            return Integer.MAX_VALUE;   // Because from protocol perspective, there is no limit
        }
    }

    /**
     * The echo protocol connection.
     */
    static class EchoProtocolConnection implements ApplicationProtocolConnection {

        private Logger log;
        private AtomicReference<ProcessSessionFactory> factoryRef = null;
        private org.slf4j.Logger nifiLogger = null;
        private final ExecutorService streamExecutor = Executors.newFixedThreadPool(100);

        public EchoProtocolConnection(QuicConnection quicConnection, Logger log,
                                      AtomicReference<ProcessSessionFactory> factoryRef,
                                      org.slf4j.Logger logger) {
            this.log = log;
            this.factoryRef = factoryRef;
            this.nifiLogger = logger;
        }

        @Override
        public void acceptPeerInitiatedStream(QuicStream quicStream) {
            // Need to handle incoming stream on separate thread; using a thread pool is recommended.
            streamExecutor.submit(() -> {
                handleEchoRequest(quicStream, factoryRef, nifiLogger);
            });
        }

        private void handleEchoRequest(QuicStream quicStream, AtomicReference<ProcessSessionFactory> factoryRef,
                                       org.slf4j.Logger nifiLogger) {
            // Hold until we get a session factory ref
            ProcessSessionFactory sessionFactory;
            do {
                sessionFactory = factoryRef.get();
                if (sessionFactory == null) {
                    try {
                        Thread.sleep(10);
                    } catch (final InterruptedException e) {
                    }
                }
            } while (sessionFactory == null);

            final ProcessSession session = sessionFactory.createSession();

            try {
                byte[] connectionHeader = new byte[2];
                int amountRead = quicStream.getInputStream().read(connectionHeader);
                while(amountRead != -1){

                    if (amountRead != 2) {
                        String readFailure = "Header bytes are not in valid V1 range.";
                        if (nifiLogger != null)
                            nifiLogger.error(readFailure);
                        throw new IOException(readFailure);
                    }

                    if (QTHelpers.bytesMatch(connectionHeader, QuicTransportConsts.PROTOCOL_V1_DATA_HEADER)) {
                        FlowFile flowFile = session.create();
                        byte[] generatedHash = QTHelpers.deserializeFlowFile(session, flowFile, quicStream.getInputStream());

                        byte[] hashBytes = new byte[QuicTransportConsts.V1_HASH_SIZE];
                        int hashBytesRead = quicStream.getInputStream().read(hashBytes);
                        if (hashBytesRead != QuicTransportConsts.V1_HASH_SIZE) {
                            String hashFailure = "Did not receive full hash after payload body.";
                            nifiLogger.error(hashFailure);
                            throw new IOException(hashFailure);
                        }

                        boolean written = false;
                        nifiLogger.debug("About to process incoming bytes.");
                        if (QTHelpers.bytesMatch(generatedHash, hashBytes)) {
                            nifiLogger.debug("Creating flowfile for incoming bytes.");
                            // Java is wild
                            session.transfer(flowFile, QuicTransportReceiver.SUCCESS);
                            session.commit();
                            nifiLogger.debug("Created flowfile sucessfully committed.");
                            written = true;
                        }

                        if (written) {
                            nifiLogger.debug("Attempting to respond with completed hash.");
                            // Return bytes regardless
                            quicStream.getOutputStream().write(generatedHash);
                            nifiLogger.debug("Completed hash sent.");
                        }
                    }
                    amountRead = quicStream.getInputStream().read(connectionHeader);
                }

            } catch (IOException e) {
                log.error("Reading quic stream failed", e);
                nifiLogger.debug("Reading quic stream failed", e);
            } catch (NoSuchAlgorithmException e) {
                // It's borked if we get here
                throw new RuntimeException(e);
            }

            // Shutdown streams - we don't care about these exceptions
            quicStream.abortReading(1);
            try {
                quicStream.getInputStream().close();
            } catch (IOException ex) {
            }
            try {
                quicStream.getOutputStream().close();
            } catch (IOException ex) {
            }
        }
    }
}
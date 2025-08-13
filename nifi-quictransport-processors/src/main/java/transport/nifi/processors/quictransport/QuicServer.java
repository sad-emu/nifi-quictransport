package transport.nifi.processors.quictransport;

import kwik.core.src.main.java.tech.kwik.core.QuicConnection;
import kwik.core.src.main.java.tech.kwik.core.QuicStream;
import kwik.core.src.main.java.tech.kwik.core.log.Logger;
import kwik.core.src.main.java.tech.kwik.core.log.SysOutLogger;
import kwik.core.src.main.java.tech.kwik.core.server.ApplicationProtocolConnection;
import kwik.core.src.main.java.tech.kwik.core.server.ApplicationProtocolConnectionFactory;
import kwik.core.src.main.java.tech.kwik.core.server.ServerConnectionConfig;
import kwik.core.src.main.java.tech.kwik.core.server.ServerConnector;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class QuicServer {

    private String certificatePath = null;
    private String keyPath = null;
    private int connectionPort = -1;
    private String protocolName;
    private boolean logPackets = false;
    private ServerConnector serverConnector = null;

    public QuicServer(String certificatePath, String keyPath, int connectionPort, String protocolName,
                      boolean logPackets) {
        this.certificatePath = certificatePath;
        this.keyPath = keyPath;
        this.connectionPort = connectionPort;
        this.protocolName = protocolName;
        this.logPackets = logPackets;
    }

    public void init() throws Exception {

        Logger log = new SysOutLogger();
        log.timeFormat(Logger.TimeFormat.Long);
        log.logWarning(true);
        log.logInfo(true);

        ServerConnectionConfig serverConnectionConfig = ServerConnectionConfig.builder()
                .maxOpenPeerInitiatedBidirectionalStreams(12)  // Mandatory setting to maximize concurrent streams on a connection.
                .build();

        this.serverConnector = ServerConnector.builder()
                .withPort(connectionPort)
                .withCertificate(new FileInputStream(certificatePath), new FileInputStream(keyPath))
                .withConfiguration(serverConnectionConfig)
                .withLogger(log)
                .build();

        registerProtocolHandler(serverConnector, log, this.protocolName);

        this.serverConnector.start();

        log.info("Started echo server on port " + connectionPort);
    }

    private static void registerProtocolHandler(ServerConnector serverConnector, Logger log, String protocolName) {
        serverConnector.registerApplicationProtocol(protocolName, new EchoProtocolConnectionFactory(log));
    }

    public void stop(){
        this.serverConnector.close();
    }

    /**
     * The factory that creates the (echo) application protocol connection.
     */
    static class EchoProtocolConnectionFactory implements ApplicationProtocolConnectionFactory {
        private final Logger log;

        public EchoProtocolConnectionFactory(Logger log) {
            this.log = log;
        }

        @Override
        public ApplicationProtocolConnection createConnection(String protocol, QuicConnection quicConnection) {
            return new EchoProtocolConnection(quicConnection, log);
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

        public EchoProtocolConnection(QuicConnection quicConnection, Logger log) {
            this.log = log;
        }

        @Override
        public void acceptPeerInitiatedStream(QuicStream quicStream) {
            // Need to handle incoming stream on separate thread; using a thread pool is recommended.
            new Thread(() -> handleEchoRequest(quicStream)).start();
        }

        private byte[] handleEchoRequest(QuicStream quicStream) {
            try {
                byte[] connectionHeader = new byte[2];
                int amountRead = quicStream.getInputStream().read(connectionHeader);

                if(amountRead != 2){
                    quicStream.getOutputStream().close();
                    throw new IOException("Header bytes are not in valid V1 range.");
                }
                if(QTHelpers.bytesMatch(connectionHeader, QuicTransportConsts.PROTOCOL_V1_HELLO_HEADER)){
                    byte[] helloBytes = new byte[QuicTransportConsts.PROTOCOL_V1_CLIENT_HELLO.length];
                    // Note that this implementation is not safe to use in the wild, as attackers can crash the server by sending arbitrary large requests.
                    amountRead = quicStream.getInputStream().read(helloBytes);
                    if(amountRead != helloBytes.length){
                        quicStream.getOutputStream().close();
                        throw new IOException("Client hello not valid.");
                    }
                    if(QTHelpers.bytesMatch(helloBytes, QuicTransportConsts.PROTOCOL_V1_CLIENT_HELLO)){
                        quicStream.getOutputStream().write(QuicTransportConsts.PROTOCOL_V1_SERVER_HELLO_ACK);
                    }
                    quicStream.getOutputStream().close();
                    return null;
                } else if (QTHelpers.bytesMatch(connectionHeader, QuicTransportConsts.PROTOCOL_V1_DATA_HEADER)) {
                    byte[] intHeader = new byte[4];
                    // Note that this implementation is not safe to use in the wild, as attackers can crash the server by sending arbitrary large requests.
                    amountRead = quicStream.getInputStream().read(intHeader);
                    if(amountRead != 4){
                        quicStream.getOutputStream().close();
                        throw new IOException("Stream failed to provide header bytes.");
                    }
                    int payloadSize = QTHelpers.bytesToInt(intHeader);
                    if(payloadSize <= 0 || payloadSize > QuicTransportConsts.MAX_V1_SIZE){
                        quicStream.getOutputStream().close();
                        throw new IOException("Header bytes are not in valid V1 range.");
                    }
                    byte[] payloadBytes = new byte[payloadSize];
                    int bytesRead = quicStream.getInputStream().read(payloadBytes);
                    if(bytesRead != payloadSize){
                        quicStream.getOutputStream().close();
                        throw new IOException("Did not receive full bytes in payload body.");
                    }
                    byte[] hashBytes = new byte[QuicTransportConsts.V1_HASH_SIZE];
                    bytesRead = quicStream.getInputStream().read(hashBytes);
                    if(bytesRead != QuicTransportConsts.V1_HASH_SIZE){
                        quicStream.getOutputStream().close();
                        throw new IOException("Did not receive full hash after payload body.");
                    }
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    byte[] generatedHash = digest.digest(payloadBytes);
                    // Return bytes regardless
                    quicStream.getOutputStream().write(generatedHash);
                    quicStream.getOutputStream().close();

                    if(QTHelpers.bytesMatch(generatedHash, hashBytes)){
                        return payloadBytes;
                    }

                } else {
                    quicStream.getOutputStream().close();
                }

            } catch (IOException e) {
                log.error("Reading quic stream failed", e);
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
            return null;
        }
    }
}
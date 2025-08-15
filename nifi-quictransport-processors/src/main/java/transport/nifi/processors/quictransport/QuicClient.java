package transport.nifi.processors.quictransport;

import kwik.core.src.main.java.tech.kwik.core.QuicClientConnection;
import kwik.core.src.main.java.tech.kwik.core.QuicStream;
import kwik.core.src.main.java.tech.kwik.core.log.Logger;
import kwik.core.src.main.java.tech.kwik.core.log.NullLogger;
import kwik.core.src.main.java.tech.kwik.core.log.SysOutLogger;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.io.InputStreamCallback;
import org.apache.nifi.util.Tuple;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.net.URI;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

public class QuicClient {
    private String serverUri = null;
    private String protocolName = null;
    private int connectionPort = -1;
    private int mtu = -1;
    private boolean logPackets = false;
    private boolean enforceCertificateCheck = false;
    //private boolean verifiedRemote = false;
    private QuicClientConnection connection = null;
    private Logger log = null;
    private final Object lock = new Object();
    private org.slf4j.Logger logger;

    public QuicClient(String serverUri, int connectionPort, String protocolName,
                      boolean logPackets, boolean enforceCertificateCheck, int mtu,
                      org.slf4j.Logger logger) {
        this.serverUri = serverUri;
        this.connectionPort = connectionPort;
        this.protocolName = protocolName;
        this.logPackets = logPackets;
        this.enforceCertificateCheck = enforceCertificateCheck;
        this.mtu = mtu;
        this.logger = logger;
    }

    private QuicClientConnection buildConnection() throws SocketException, UnknownHostException {
        String connectionUri = this.protocolName + "://" + this.serverUri + ":" + this.connectionPort;
        QuicClientConnection builtCon = null;
        if (this.enforceCertificateCheck) {
            builtCon = QuicClientConnection.newBuilder()
                    .uri(URI.create(connectionUri))
                    .applicationProtocol(this.protocolName)
                    .logger(log)
                    .build(this.mtu);
        } else {
            builtCon = QuicClientConnection.newBuilder()
                    .uri(URI.create(connectionUri))
                    .applicationProtocol(this.protocolName)
                    .noServerCertificateCheck()
                    .logger(log)
                    .build(this.mtu);
        }

        return builtCon;
    }

    public boolean init() throws SocketException, UnknownHostException {
        log = new NullLogger();
        // Use a real logger if we are debugging
        if (this.logPackets) {
            log = new SysOutLogger();
        }
        log.logPackets(this.logPackets);
        synchronized (this.lock){
            connection = buildConnection();
        }

        return connection.isConnected();//this.verifyRemote();
    }

    private boolean reconnected() {
        if(connection.isConnected())
            return true;
        synchronized (this.lock){
            try {
                connection.connect();
            } catch (IllegalStateException e) {
                try {
                    connection = buildConnection();
                    connection.connect();
                } catch (IOException ex) {
                    return false;
                }

            } catch (IOException excs){
                return false;
            }
            return true;
        }
    }

    public void send(List<FlowFile> payloads, final ProcessSession session, List<FlowFile> success, List<FlowFile> failures) throws IOException {
        final long startTime = System.currentTimeMillis();
        this.logger.info("Send start time (ms): " + startTime);
        if(!reconnected()){
            throw new IOException("Could not connect to endpoint");
        }
        long reconnectTime = System.currentTimeMillis();
        this.logger.info("Reconnect took (ms): " + (reconnectTime - startTime));
        long streamCreate = System.currentTimeMillis();
        QuicStream quicStream = connection.createStream(true);
        this.logger.info("streamCreate took (ms): " + (System.currentTimeMillis() - streamCreate));

        try {
            for(FlowFile payload : payloads) {

                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                long attributeWrite = System.currentTimeMillis();

                quicStream.getOutputStream().write(QuicTransportConsts.PROTOCOL_V1_DATA_HEADER);
                byte[] flowfileAttributes = QTHelpers.serialiseFlowFileAttributes(payload);
                long attributesLen = flowfileAttributes.length;
                byte[] attributesLenBytes = QTHelpers.longToBytes(attributesLen);
                quicStream.getOutputStream().write(attributesLenBytes);
                quicStream.getOutputStream().write(flowfileAttributes);

                this.logger.info("Write attributes & header took (ms): " + (System.currentTimeMillis() - attributeWrite));
                long payloadStart = System.currentTimeMillis();

                long payloadLen = payload.getSize();
                byte[] payloadLenBytes = QTHelpers.longToBytes(payloadLen);
                quicStream.getOutputStream().write(payloadLenBytes);

                session.read(payload, in -> {
                    byte[] buffer = new byte[QuicTransportConsts.BUFFER_SIZE];
                    int len;
                    //int loops = 0;
                    //long loopstart = System.currentTimeMillis();
                    while ((len = in.read(buffer)) != -1) {
                        //this.logger.info("Read took (ms): " + (System.currentTimeMillis() - loopstart));
                        //long hashstart = System.currentTimeMillis();
                        digest.update(buffer, 0, len);
                        //this.logger.info("Hash took (ms): " + (System.currentTimeMillis() - hashstart));
                        //long writeStart = System.currentTimeMillis();
                        quicStream.getOutputStream().write(buffer, 0, len);
                        //this.logger.info("Write took (ms): " + (System.currentTimeMillis() - writeStart));
                        //loopstart = System.currentTimeMillis();
                        //loops += 1;
                    }
                    //this.logger.info("Write took operations: " + (loops));
                });

                this.logger.info("Payloadstart + send took (ms): " + (System.currentTimeMillis() - payloadStart));
                long hashHandshake = System.currentTimeMillis();

                byte[] expectedHash = digest.digest();
                quicStream.getOutputStream().write(expectedHash);
                byte[] responseHash = new byte[expectedHash.length];

                if (quicStream.getInputStream().read(responseHash) != expectedHash.length) {
                    quicStream.getInputStream().close();
                    throw new IOException("Response hash the incorrect length.");
                }
                if (!QTHelpers.bytesMatch(responseHash, expectedHash)) {
                    quicStream.getInputStream().close();
                    throw new IOException("Wrong response bytes in server response.");
                }
                session.transfer(payload, QuicTransportSender.SUCCESS);
                success.add(payload);
                this.logger.info("hashhandshake + send took (ms): " + (System.currentTimeMillis() - hashHandshake));
            }
            quicStream.getOutputStream().close();
            quicStream.getInputStream().close();
            // This means we can duplicate but not lose data.
        } catch (Exception exc){
            //this.verifiedRemote = false;
            try {
                quicStream.getOutputStream().close();
            } catch (Exception excs1) {

            }
            try {
                quicStream.getInputStream().close();
            } catch (Exception excs2) {

            }
            throw new IOException("Error in send. " + exc.getMessage());
        }

        if(success.size() != payloads.size()){
            for(FlowFile fileAttempted : payloads){
                if(!success.contains(fileAttempted))
                    failures.add(fileAttempted);
            }
        }

        this.logger.info("fullsend took (ms): " + (System.currentTimeMillis() - startTime));
    }
}
package transport.nifi.processors.quictransport;

import kwik.core.src.main.java.tech.kwik.core.QuicClientConnection;
import kwik.core.src.main.java.tech.kwik.core.QuicStream;
import kwik.core.src.main.java.tech.kwik.core.log.Logger;
import kwik.core.src.main.java.tech.kwik.core.log.NullLogger;
import kwik.core.src.main.java.tech.kwik.core.log.SysOutLogger;

import java.io.IOException;
import java.net.SocketException;
import java.net.URI;
import java.net.UnknownHostException;
import java.security.MessageDigest;

public class QuicClient {
    private String serverUri = null;
    private String protocolName = null;
    private int connectionPort = -1;
    private boolean logPackets = false;
    private boolean enforceCertificateCheck = false;
    //private boolean verifiedRemote = false;
    private QuicClientConnection connection = null;
    private Logger log = null;
    private final Object lock = new Object();

    public QuicClient(String serverUri, int connectionPort, String protocolName,
                      boolean logPackets, boolean enforceCertificateCheck) {
        this.serverUri = serverUri;
        this.connectionPort = connectionPort;
        this.protocolName = protocolName;
        this.logPackets = logPackets;
        this.enforceCertificateCheck = enforceCertificateCheck;
    }

    private QuicClientConnection buildConnection() throws SocketException, UnknownHostException {
        String connectionUri = this.protocolName + "://" + this.serverUri + ":" + this.connectionPort;
        QuicClientConnection builtCon = null;
        if (this.enforceCertificateCheck) {
            builtCon = QuicClientConnection.newBuilder()
                    .uri(URI.create(connectionUri))
                    .applicationProtocol(this.protocolName)
                    .logger(log)
                    .build();
        } else {
            builtCon = QuicClientConnection.newBuilder()
                    .uri(URI.create(connectionUri))
                    .applicationProtocol(this.protocolName)
                    .noServerCertificateCheck()
                    .logger(log)
                    .build();
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
        synchronized (this.lock){
            if(connection.isConnected())
                return true;
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


//    private String verifyRemote() {
//        String returnString = null;
//        if (!reconnected()) {
//            return "Reconnection failed.";
//        }
//        try {
//            QuicStream quicStream = connection.createStream(true);
//            quicStream.getOutputStream().write(QuicTransportConsts.PROTOCOL_V1_HELLO_HEADER);
//            quicStream.getOutputStream().write(QuicTransportConsts.PROTOCOL_V1_CLIENT_HELLO);
//            quicStream.getOutputStream().close();
//
//            byte[] respBuffer = new byte[QuicTransportConsts.PROTOCOL_V1_SERVER_HELLO_ACK.length];
//            int respRead = quicStream.getInputStream().read(respBuffer);
//
//            if (respRead != QuicTransportConsts.PROTOCOL_V1_SERVER_HELLO_ACK.length) {
//                returnString = "Wrong response length for protocol ack.";
//            }
//
//            for (int i = 0; i < QuicTransportConsts.PROTOCOL_V1_SERVER_HELLO_ACK.length; i++) {
//                if (respBuffer[i] != QuicTransportConsts.PROTOCOL_V1_SERVER_HELLO_ACK[i]) {
//
//                    returnString = "Wrong response bytes for protocol ack.";
//                    break;
//                }
//            }
//            if (returnString == null) {
//                this.verifiedRemote = true;
//            }
//            quicStream.abortReading(1);
//            quicStream.getInputStream().close();
//        } catch (IOException exc) {
//            returnString = "Unable to verify remote host on startup.";
//        }
//        return returnString;
//
//    }

    // TODO update to streams
    public void send(byte[] payload) throws IOException {
        if(!reconnected()){
            throw new IOException("Could not connect to endpoint");
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] expectedHash = digest.digest(payload);
            if(expectedHash.length != QuicTransportConsts.V1_HASH_SIZE){
                throw new IOException("Sending hash is the wrong number of bytes for V1");
            }
            QuicStream quicStream = connection.createStream(true);
            byte[] dataLenBytes = QTHelpers.intToBytes(payload.length);
            quicStream.getOutputStream().write(QuicTransportConsts.PROTOCOL_V1_DATA_HEADER);
            quicStream.getOutputStream().write(dataLenBytes);
            quicStream.getOutputStream().write(payload);
            quicStream.getOutputStream().write(expectedHash);
            quicStream.getOutputStream().close();

            byte[] responseHash = new byte[expectedHash.length];

            if (quicStream.getInputStream().read(responseHash) != expectedHash.length) {
                quicStream.getInputStream().close();
                throw new IOException("Response hash the incorrect length.");
            }
            if(!QTHelpers.bytesMatch(responseHash, expectedHash)){
                quicStream.getInputStream().close();
                throw new IOException("Wrong response bytes in server response.");
            }
            quicStream.getInputStream().close();
            // This means we can duplicate but not lose data.
            return ;
        } catch (Exception exc){
            //this.verifiedRemote = false;
            throw new IOException("Error in send. " + exc.getMessage());
        }
    }
}
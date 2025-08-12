package transport.nifi.processors.quictransport;

import tech.kwik.core.QuicClientConnection;
import tech.kwik.core.QuicStream;
import tech.kwik.core.log.Logger;
import tech.kwik.core.log.NullLogger;
import tech.kwik.core.log.SysOutLogger;

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
    private boolean verifiedRemote = false;
    private QuicClientConnection connection = null;

    public QuicClient(String serverUri, int connectionPort, String protocolName,
                      boolean logPackets, boolean enforceCertificateCheck) {
        this.serverUri = serverUri;
        this.connectionPort = connectionPort;
        this.protocolName = protocolName;
        this.logPackets = logPackets;
        this.enforceCertificateCheck = enforceCertificateCheck;
    }

    public String init() throws SocketException, UnknownHostException {
        Logger log = new NullLogger();

        // Use a real logger if we are debugging
        if (this.logPackets) {
            log = new SysOutLogger();
        }
        log.logPackets(this.logPackets);

        connection = null;
        String connectionUri = this.protocolName + "://" + this.serverUri + ":" + this.connectionPort;
        if (this.enforceCertificateCheck) {
            connection = QuicClientConnection.newBuilder()
                    .uri(URI.create(connectionUri))
                    .applicationProtocol(this.protocolName)
                    .logger(log)
                    .build();
        } else {
            connection = QuicClientConnection.newBuilder()
                    .uri(URI.create(connectionUri))
                    .applicationProtocol(this.protocolName)
                    .noServerCertificateCheck()
                    .logger(log)
                    .build();
        }

        return this.verifyRemote();
    }

    private String verifyRemote(){
        String returnString = null;
        try {
            connection.connect();

            QuicStream quicStream = connection.createStream(true);

            quicStream.getOutputStream().write(QuicTransportConsts.PROTOCOL_V1_CLIENT_HELLO);
            quicStream.getOutputStream().close();

            byte[] respBuffer = new byte[QuicTransportConsts.PROTOCOL_V1_SERVER_ACK.length];
            int respRead = quicStream.getInputStream().read(respBuffer);

            if (respRead != QuicTransportConsts.PROTOCOL_V1_SERVER_ACK.length) {
                returnString = "Wrong response length for protocol ack.";
            }

            for (int i = 0; i < QuicTransportConsts.PROTOCOL_V1_SERVER_ACK.length; i++) {
                if (respBuffer[i] != QuicTransportConsts.PROTOCOL_V1_SERVER_ACK[i]) {

                    returnString = "Wrong response bytes for protocol ack.";
                    break;
                }
            }
            if (returnString == null) {
                this.verifiedRemote = true;
            }
        } catch (IOException exc) {
            returnString = "Unable to verify remote host on startup.";
        } finally {
            connection.closeAndWait();
        }
        return returnString;
    }

    // TODO update to allow parts?
    public String send(byte[] payload) {
        if(!verifiedRemote){
            String verifyString = this.verifyRemote();
            if(verifyString != null){
                return verifyString;
            }
            this.verifiedRemote = true;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] expectedHash = digest.digest(payload);
            if(expectedHash.length != QuicTransportConsts.V1_HASH_SIZE){
                return "Sending hash is the wrong number of bytes for V1";
            }
            QuicStream quicStream = connection.createStream(true);
            byte[] dataLenBytes = QTHelpers.intToBytes(payload.length);
            quicStream.getOutputStream().write(dataLenBytes);
            quicStream.getOutputStream().write(payload);
            quicStream.getOutputStream().write(expectedHash);
            quicStream.getOutputStream().close();

            byte[] responseHash = new byte[expectedHash.length];

            if (quicStream.getInputStream().read(responseHash) != expectedHash.length) {
                return "Response hash the incorrect length.";
            }
            for (int i = 0; i < expectedHash.length; i++) {
                if (responseHash[i] != expectedHash[i]) {
                    return "Wrong response bytes in server response.";
                }
            }
            // This means we can duplicate but not lose data.
            return null;
        } catch (Exception exc){
            this.verifiedRemote = false;
            return "Error in send. " + exc.getMessage();
        }
    }
}
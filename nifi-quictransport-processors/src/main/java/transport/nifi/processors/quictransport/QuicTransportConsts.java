package transport.nifi.processors.quictransport;

public class QuicTransportConsts {
    public static final String PROTOCOL_V1 = "qtprotov1";
    public static final byte[] PROTOCOL_V1_CLIENT_HELLO = { 113, 116, 112, 114, 111, 116, 111, 118, 49, 99,
                                                            108, 105, 101, 110, 116, 104, 101, 108, 108, 111 };
    public static final byte[] PROTOCOL_V1_SERVER_ACK = { 113, 116, 112, 114, 111, 116, 111, 118, 49, 115, 101,
                                                            114, 118, 101, 114, 97, 99, 107 };
    public static final int MAX_V1_SIZE = 20971520; // 20Mb
    public static final int V1_HASH_SIZE = 32; // Sha256
}

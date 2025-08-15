package transport.nifi.processors.quictransport;

public class QuicTransportConsts {
    public static final String PROTOCOL_V1 = "qtprotov1";
    public static final byte[] PROTOCOL_V1_HELLO_HEADER = {1, 2};
    public static final byte[] PROTOCOL_V1_DATA_HEADER = {2, 3};
    public static final byte[] PROTOCOL_V1_CLIENT_HELLO = { 113, 116, 112, 114, 111, 116, 111, 118, 49, 99,
                                                            108, 105, 101, 110, 116, 104, 101, 108, 108, 111 };
    public static final byte[] PROTOCOL_V1_SERVER_HELLO_ACK = { 113, 116, 112, 114, 111, 116, 111, 118, 49, 115, 101,
                                                            114, 118, 101, 114, 97, 99, 107 };
    public static final long MAX_V1_SIZE = Long.MAX_VALUE; // 20Mb
    public static final int V1_HASH_SIZE = 32; // Sha256
    public static final int BUFFER_SIZE = 1024*1024 * 1; // 1mb //131072; // 128k
}

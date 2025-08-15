package transport.nifi.processors.quictransport;

import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.ProcessSession;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

public class QTHelpers {

    public static byte[] longToBytes(long longIn){
        return new byte[] {
                (byte)(longIn >>> 56),
                (byte)(longIn >>> 48),
                (byte)(longIn >>> 40),
                (byte)(longIn >>> 32),
                (byte)(longIn >>> 24),
                (byte)(longIn >>> 16),
                (byte)(longIn >>> 8),
                (byte)longIn};
    }
    public static long bytesToLong(byte[] bytesIn) throws IllegalArgumentException{
        if (bytesIn.length != 8) {
            throw new IllegalArgumentException("Byte array must be exactly 8 bytes long");
        }
        return ((long)(bytesIn[0] & 0xFF) << 56) |
                ((long)(bytesIn[1] & 0xFF) << 48) |
                ((long)(bytesIn[2] & 0xFF) << 40) |
                ((long)(bytesIn[3] & 0xFF) << 32) |
                ((long)(bytesIn[4] & 0xFF) << 24) |
                ((long)(bytesIn[5] & 0xFF) << 16) |
                ((long)(bytesIn[6] & 0xFF) << 8) |
                ((long)(bytesIn[7] & 0xFF));
    }

    public static byte[] intToBytes(int intIn){
        return new byte[] {
                (byte)(intIn >>> 24),
                (byte)(intIn >>> 16),
                (byte)(intIn >>> 8),
                (byte)intIn};
    }
    public static int bytesToInt(byte[] bytesIn) throws IllegalArgumentException{
        if (bytesIn.length != 4) {
            throw new IllegalArgumentException("Byte array must be exactly 4 bytes long");
        }
        return ((bytesIn[0] & 0xFF) << 24) |
                ((bytesIn[1] & 0xFF) << 16) |
                ((bytesIn[2] & 0xFF) << 8) |
                (bytesIn[3] & 0xFF);
    }
    public static boolean bytesMatch(byte[] inA, byte[] inB){
        if(inA.length != inB.length){
            return false;
        }
        for(int i = 0; i < inA.length; i++){
            if(inA[i] != inB[i]){
                return false;
            }
        }
        return true;
    }

    public static byte[] serialiseFlowFileAttributes(FlowFile flowFile) throws IOException{
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream dataOut = new DataOutputStream(out);

        // Write attributes
        Map<String, String> attributes = flowFile.getAttributes();
        dataOut.writeInt(attributes.size());
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            dataOut.writeUTF(entry.getKey());
            dataOut.writeUTF(entry.getValue());
        }
        dataOut.flush();
        return out.toByteArray();
    }

    public static byte[] serializeFlowFile(ProcessSession session, FlowFile flowFile) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream dataOut = new DataOutputStream(out);

        // Write attributes
        Map<String, String> attributes = flowFile.getAttributes();
        dataOut.writeInt(attributes.size());
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            dataOut.writeUTF(entry.getKey());
            dataOut.writeUTF(entry.getValue());
        }

        // Write content
        ByteArrayOutputStream contentOut = new ByteArrayOutputStream();
        session.read(flowFile, in -> {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) > 0) {
                contentOut.write(buffer, 0, len);
            }
        });

        byte[] contentBytes = contentOut.toByteArray();
        dataOut.writeInt(contentBytes.length);
        dataOut.write(contentBytes);

        dataOut.flush();
        return out.toByteArray();
    }

    public static long readLong(InputStream netStream) throws IOException {
        byte[] bytesIn = new byte[8];
        int bytesRead = netStream.read(bytesIn);
        if(bytesRead != 8)
            throw new IOException("Incorrect number of bytes available to read long.");
        return bytesToLong(bytesIn);
    }

    public static int readInt(InputStream netStream) throws IOException {
        byte[] bytesIn = new byte[4];
        int bytesRead = netStream.read(bytesIn);
        if(bytesRead != 4)
            throw new IOException("Incorrect number of bytes available to read int.");
        return bytesToInt(bytesIn);
    }

    public static String readUTF(InputStream in) throws IOException {
        // Read 2 bytes for length (unsigned short)
        int byte1 = in.read();
        int byte2 = in.read();

        if (byte1 == -1 || byte2 == -1) {
            throw new EOFException("Stream ended before reading UTF length.");
        }

        int length = ((byte1 & 0xFF) << 8) | (byte2 & 0xFF);

        // Read 'length' bytes of UTF-8 data
        byte[] utfBytes = new byte[length];
        int totalRead = 0;
        while (totalRead < length) {
            int read = in.read(utfBytes, totalRead, length - totalRead);
            if (read == -1) {
                throw new EOFException("Stream ended before full UTF data was read.");
            }
            totalRead += read;
        }

        // Decode UTF-8
        return new String(utfBytes, StandardCharsets.UTF_8);
    }

    public static byte[] deserializeFlowFile(ProcessSession session,
                                               FlowFile flowFile, InputStream netStream) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        long attributesLen = readLong(netStream);

        // Read attributes
        int attrCount = readInt(netStream);
        Map<String, String> attributes = new HashMap<>();
        for (int i = 0; i < attrCount; i++) {
            String key = readUTF(netStream);
            String value = readUTF(netStream);
            attributes.put(key, value);
        }

        long payloadLen = readLong(netStream);;

        flowFile = session.write(flowFile, out -> {
            byte[] buffer = new byte[QuicTransportConsts.BUFFER_SIZE];
            long amountRead = 0;
            while(amountRead < payloadLen){
                if(amountRead + buffer.length > payloadLen){
                    buffer = new byte[((int) (payloadLen - amountRead))];
                }

                int bufferRead = netStream.read(buffer);
                if (bufferRead == -1){
                    break;
                }
                amountRead += bufferRead;
                digest.update(buffer, 0, bufferRead);
                out.write(buffer, 0, bufferRead);
            }
        });

        // Apply attributes on the file
        session.putAllAttributes(flowFile, attributes);

        return digest.digest();
    }
}

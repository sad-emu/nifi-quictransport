package transport.nifi.processors.quictransport;

import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.ProcessSession;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class QTHelpers {
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

    public static FlowFile deserializeFlowFile(ProcessSession session, byte[] data) throws IOException {
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        DataInputStream dataIn = new DataInputStream(in);

        // Read attributes
        int attrCount = dataIn.readInt();
        Map<String, String> attributes = new HashMap<>();
        for (int i = 0; i < attrCount; i++) {
            String key = dataIn.readUTF();
            String value = dataIn.readUTF();
            attributes.put(key, value);
        }

        // Read content
        int contentLength = dataIn.readInt();
        byte[] contentBytes = new byte[contentLength];
        dataIn.readFully(contentBytes);

        // Create FlowFile
        FlowFile flowFile = session.create();
        flowFile = session.putAllAttributes(flowFile, attributes);

        flowFile = session.write(flowFile, out -> {
            out.write(contentBytes);
        });

        return flowFile;
    }
}

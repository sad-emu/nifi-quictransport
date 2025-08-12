package transport.nifi.processors.quictransport;

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
}

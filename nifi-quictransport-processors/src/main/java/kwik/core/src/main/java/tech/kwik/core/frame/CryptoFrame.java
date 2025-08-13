/*
 * Copyright © 2019, 2020, 2021, 2022, 2023, 2024, 2025 Peter Doornbosch
 *
 * This file is part of Kwik, an implementation of the QUIC protocol in Java.
 *
 * Kwik is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Kwik is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package kwik.core.src.main.java.tech.kwik.core.frame;


import kwik.core.src.main.java.tech.kwik.core.generic.IntegerTooLargeException;
import kwik.core.src.main.java.tech.kwik.core.generic.InvalidIntegerEncodingException;
import kwik.core.src.main.java.tech.kwik.core.generic.VariableLengthInteger;
import kwik.core.src.main.java.tech.kwik.core.impl.Version;
import kwik.core.src.main.java.tech.kwik.core.log.Logger;
import kwik.core.src.main.java.tech.kwik.core.packet.PacketMetaData;
import kwik.core.src.main.java.tech.kwik.core.packet.QuicPacket;
import kwik.core.src.main.java.tech.kwik.core.stream.StreamElement;

import java.nio.ByteBuffer;

/**
 * Represents a crypto frame.
 * https://www.rfc-editor.org/rfc/rfc9000.html#name-crypto-frames
 */
public class CryptoFrame extends QuicFrame implements StreamElement {

    private long offset;
    private int length;
    private byte[] cryptoData;
    private byte[] bytes;

    public CryptoFrame() {
    }

    public CryptoFrame(Version quicVersion, byte[] payload) {
        this(quicVersion, 0, payload);
    }

    public CryptoFrame(Version quicVersion, long offset, byte[] payload) {
        this.offset = offset;
        cryptoData = payload;
        length = payload.length;
        ByteBuffer frameBuffer = ByteBuffer.allocate(3 * 4 + payload.length);
        VariableLengthInteger.encode(0x06, frameBuffer);
        VariableLengthInteger.encode(offset, frameBuffer);
        VariableLengthInteger.encode(payload.length, frameBuffer);
        frameBuffer.put(payload);

        bytes = new byte[frameBuffer.position()];
        frameBuffer.rewind();
        frameBuffer.get(bytes);
    }

    @Override
    public int getFrameLength() {
        return 1
                + VariableLengthInteger.bytesNeeded(offset)
                + VariableLengthInteger.bytesNeeded(cryptoData.length)
                + cryptoData.length;
    }

    @Override
    public void serialize(ByteBuffer buffer) {
        buffer.put((byte) 0x06);
        VariableLengthInteger.encode(offset, buffer);
        VariableLengthInteger.encode(cryptoData.length, buffer);
        buffer.put(cryptoData);
    }

    public CryptoFrame parse(ByteBuffer buffer, Logger log) throws InvalidIntegerEncodingException, IntegerTooLargeException {
        log.debug("Parsing Crypto frame");
        buffer.get();

        offset = VariableLengthInteger.parseLong(buffer);
        length = VariableLengthInteger.parseInt(buffer);

        cryptoData = new byte[length];
        buffer.get(cryptoData);
        log.decrypted("Crypto data [" + offset + "," + length + "]", cryptoData);

        return this;
    }

    @Override
    public String toString() {
        return "CryptoFrame[" + offset + "," + length + "]";
    }

    public byte[] getStreamData() {
        return cryptoData;
    }

    @Override
    public long getOffset() {
        return offset;
    }

    @Override
    public int getLength() {
        return length;
    }

    @Override
    public long getUpToOffset() {
        return offset + length;
    }

    @Override
    public boolean isFinal() {
        return false;
    }

    @Override
    public int compareTo(StreamElement other) {
        if (this.offset != other.getOffset()) {
            return Long.compare(this.offset, other.getOffset());
        }
        else {
            return Long.compare(this.length, other.getLength());
        }
    }

    public void accept(FrameProcessor frameProcessor, QuicPacket packet, PacketMetaData metaData) {
        frameProcessor.process(this, packet, metaData);
    }
}

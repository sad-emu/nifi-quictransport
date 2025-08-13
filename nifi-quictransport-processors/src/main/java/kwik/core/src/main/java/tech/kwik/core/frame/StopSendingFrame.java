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

import kwik.core.src.main.java.tech.kwik.core.generic.InvalidIntegerEncodingException;
import kwik.core.src.main.java.tech.kwik.core.generic.VariableLengthInteger;
import kwik.core.src.main.java.tech.kwik.core.impl.TransportError;
import kwik.core.src.main.java.tech.kwik.core.impl.Version;
import kwik.core.src.main.java.tech.kwik.core.log.Logger;
import kwik.core.src.main.java.tech.kwik.core.packet.PacketMetaData;
import kwik.core.src.main.java.tech.kwik.core.packet.QuicPacket;

import java.nio.ByteBuffer;

/**
 * Represents a stop sending frame.
 * https://www.rfc-editor.org/rfc/rfc9000.html#name-stop_sending-frames
 */
public class StopSendingFrame extends QuicFrame {

    private int streamId;
    private long errorCode;

    public StopSendingFrame(Version quicVersion) {
    }

    public StopSendingFrame(Version quicVersion, Integer streamId, Long errorCode) {
        this.streamId = streamId;
        this.errorCode = errorCode;
    }

    public StopSendingFrame parse(ByteBuffer buffer, Logger log) throws InvalidIntegerEncodingException, TransportError {
        buffer.get();

        streamId = parseVariableLengthIntegerLimitedToInt(buffer);  // Kwik does not support stream id's larger than max int.
        errorCode = VariableLengthInteger.parseLong(buffer);

        return this;
    }

    @Override
    public int getFrameLength() {
        return 1 + VariableLengthInteger.bytesNeeded(streamId)
                + VariableLengthInteger.bytesNeeded(errorCode);
    }

    @Override
    public void serialize(ByteBuffer buffer) {
        buffer.put((byte) 0x05);
        VariableLengthInteger.encode(streamId, buffer);
        VariableLengthInteger.encode(errorCode, buffer);
    }

    @Override
    public String toString() {
        return "StopSendingFrame[" + streamId + ":" + errorCode + "]";
    }

    @Override
    public void accept(FrameProcessor frameProcessor, QuicPacket packet, PacketMetaData metaData) {
        frameProcessor.process(this, packet, metaData);
    }

    public int getStreamId() {
        return streamId;
    }

    public long getErrorCode() {
        return errorCode;
    }
}

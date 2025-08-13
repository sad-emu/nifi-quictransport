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
import kwik.core.src.main.java.tech.kwik.core.impl.Version;
import kwik.core.src.main.java.tech.kwik.core.log.Logger;
import kwik.core.src.main.java.tech.kwik.core.packet.PacketMetaData;
import kwik.core.src.main.java.tech.kwik.core.packet.QuicPacket;

import java.nio.ByteBuffer;


/**
 * Represents a streams blocked frame.
 * https://www.rfc-editor.org/rfc/rfc9000.html#name-streams_blocked-frames
 */
public class StreamsBlockedFrame extends QuicFrame {

    private boolean bidirectional;
    private long streamLimit;

    public StreamsBlockedFrame() {
    }

    public StreamsBlockedFrame(Version quicVersion, boolean bidirectional, int streamLimit) {
        this.bidirectional = bidirectional;
        this.streamLimit = streamLimit;
    }

    public StreamsBlockedFrame parse(ByteBuffer buffer, Logger log) throws InvalidIntegerEncodingException {
        byte frameType = buffer.get();
        bidirectional = frameType == 0x16;
        streamLimit = VariableLengthInteger.parseLong(buffer);

        return this;
    }

    @Override
    public int getFrameLength() {
        return 1 + VariableLengthInteger.bytesNeeded(streamLimit);
    }

    @Override
    public void serialize(ByteBuffer buffer) {
        // https://www.rfc-editor.org/rfc/rfc9000.html#name-streams_blocked-frames
        // "A STREAMS_BLOCKED frame of type 0x16 is used to indicate reaching the bidirectional stream limit, and a
        // STREAMS_BLOCKED frame of type 0x17 is used to indicate reaching the unidirectional stream limit."
        buffer.put(bidirectional? (byte) 0x16: (byte) 0x17);
        VariableLengthInteger.encode(streamLimit, buffer);
    }

    @Override
    public String toString() {
        return "StreamsBlockedFrame[" + (bidirectional? "B": "U") + "|" + streamLimit + "]";
    }

    @Override
    public void accept(FrameProcessor frameProcessor, QuicPacket packet, PacketMetaData metaData) {
        frameProcessor.process(this, packet, metaData);
    }

    public boolean isBidirectional() {
        return bidirectional;
    }

    public long getStreamLimit() {
        return streamLimit;
    }
}

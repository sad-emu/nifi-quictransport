/*
 * Copyright © 2020, 2021, 2022, 2023, 2024, 2025 Peter Doornbosch
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
package kwik.core.src.main.java.tech.kwik.core;

import kwik.core.src.main.java.tech.kwik.core.send.SendStatistics;

import java.util.Objects;

public class Statistics {

    private final SendStatistics senderStatistics;

    public Statistics(SendStatistics statistics) {
        senderStatistics = Objects.requireNonNull(statistics);
    }

    @SuppressWarnings("NarrowCalculation")
    public float efficiency() {
        return senderStatistics.bytesSent() > 0? (float) ((senderStatistics.dataBytesSent() * 10000 / senderStatistics.bytesSent()) / 100.0) : 0;
    }

    public int datagramsSent() {
        return senderStatistics.datagramsSent();
    }

    public long bytesSent() {
        return senderStatistics.bytesSent();
    }

    public long dataBytesSent() {
        return senderStatistics.dataBytesSent();
    }

    public long lostPackets() {
        return senderStatistics.lostPackets();
    }

    public long packetsSent() {
        return senderStatistics.packetsSent();
    }

    public int smoothedRtt() {
        return senderStatistics.smoothedRtt();
    }

    public int rttVar() {
        return senderStatistics.rttVar();
    }

    public int latestRtt() {
        return senderStatistics.latestRtt();
    }

    @Override
    public String toString() {
        return String.format(
                "datagrams sent: %d\npackets send: %d\nbytes sent: %d\ndata sent: %d\nefficieny: %.2f\npackets lost: %d" +
                "\nsmoothed RTT: %d\nRTT var: %d\nlatest RTT: %d",
                senderStatistics.datagramsSent(), senderStatistics.packetsSent(), senderStatistics.bytesSent(),
                senderStatistics.dataBytesSent(),
                efficiency(),
                senderStatistics.lostPackets(),
                senderStatistics.smoothedRtt(), senderStatistics.rttVar(), senderStatistics.latestRtt());
    }
}

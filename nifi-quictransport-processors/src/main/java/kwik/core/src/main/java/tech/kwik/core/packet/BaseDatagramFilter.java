/*
 * Copyright © 2024, 2025 Peter Doornbosch
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
package kwik.core.src.main.java.tech.kwik.core.packet;

import kwik.core.src.main.java.tech.kwik.core.impl.TransportError;
import kwik.core.src.main.java.tech.kwik.core.log.Logger;
import kwik.core.src.main.java.tech.kwik.core.log.NullLogger;

import java.nio.ByteBuffer;

public abstract class BaseDatagramFilter implements DatagramFilter {

    private final DatagramFilter next;
    private final Logger log;

    public BaseDatagramFilter(DatagramFilter next) {
        this.next = next;
        log = new NullLogger();
    }

    public BaseDatagramFilter(DatagramFilter next, Logger log) {
        this.next = next;
        this.log = log != null ? log : new NullLogger();
    }

    public BaseDatagramFilter(BaseDatagramFilter next) {
        this.next = next;
        this.log = next.logger();
    }

    public void next(ByteBuffer data, PacketMetaData metaData) throws TransportError {
        next.processDatagram(data, metaData);
    }

    protected void discard(ByteBuffer data, PacketMetaData metaData, String reason) {
        logger().debug("Discarding datagram : " + reason);
    }

    protected Logger logger() {
        return log;
    }
}

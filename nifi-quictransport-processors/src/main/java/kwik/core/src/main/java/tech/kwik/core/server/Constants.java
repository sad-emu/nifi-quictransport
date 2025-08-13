/*
 * Copyright © 2023, 2024, 2025 Peter Doornbosch
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
package kwik.core.src.main.java.tech.kwik.core.server;


public class Constants {

    /**
     * The maximum length of a connection ID.
     * https://www.rfc-editor.org/rfc/rfc9000.html#name-long-header-packets
     * "In QUIC version 1, this value MUST NOT exceed 20 bytes."
     */
    public static final int MAXIMUM_CONNECTION_ID_LENGTH = 20;

    /**
     * The minimum length of a connection ID.
     * This is an implementation choice, not a protocol requirement.
     * Because this server implementation uses one port for all connections,
     * the connection ID must be long enough to prevent accidental collisions.
     */
    public static final int MINIMUM_CONNECTION_ID_LENGTH = 4;
}

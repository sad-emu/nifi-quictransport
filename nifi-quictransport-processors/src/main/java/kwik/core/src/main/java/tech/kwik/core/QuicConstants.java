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
package kwik.core.src.main.java.tech.kwik.core;


public class QuicConstants {

    // https://www.rfc-editor.org/rfc/rfc9000.html#section-18.2
    public enum TransportParameterId {
        original_destination_connection_id (0),
        max_idle_timeout(1),
        stateless_reset_token(2),
        max_udp_payload_size(3),
        initial_max_data(4),
        initial_max_stream_data_bidi_local(5),
        initial_max_stream_data_bidi_remote(6),
        initial_max_stream_data_uni(7),
        initial_max_streams_bidi(8),
        initial_max_streams_uni(9),
        ack_delay_exponent(0x0a),
        max_ack_delay(0x0b),
        disable_active_migration(0x0c),
        preferred_address(0x0d),
        active_connection_id_limit(0x0e),
        initial_source_connection_id(0x0f),
        retry_source_connection_id(0x10),
        // https://www.rfc-editor.org/rfc/rfc9368.html#section-10.1
        version_information(0x11),
        ;
        public final int value;

        TransportParameterId(int value) {
            this.value = value;
        }
    }

    // https://www.rfc-editor.org/rfc/rfc9000.html#section-20.1
    public enum TransportErrorCode {
        NO_ERROR (0x0),
        INTERNAL_ERROR (0x1),
        CONNECTION_REFUSED (0x2),
        FLOW_CONTROL_ERROR (0x3),
        STREAM_LIMIT_ERROR (0x4),
        STREAM_STATE_ERROR (0x5),
        FINAL_SIZE_ERROR (0x6),
        FRAME_ENCODING_ERROR (0x7),
        TRANSPORT_PARAMETER_ERROR (0x8),
        CONNECTION_ID_LIMIT_ERROR (0x9),
        PROTOCOL_VIOLATION (0xa),
        INVALID_TOKEN (0xb),
        APPLICATION_ERROR (0xc),
        CRYPTO_BUFFER_EXCEEDED (0xd),
        KEY_UPDATE_ERROR (0xe),
        AEAD_LIMIT_REACHED (0xf),
        NO_VIABLE_PATH (0x10),
        CRYPTO_ERROR (0x100),
        // https://www.rfc-editor.org/rfc/rfc9368.html#section-10.2
        VERSION_NEGOTIATION_ERROR(0x11),
        ;

        public final int value;

        TransportErrorCode(int value) {
            this.value = value;
        }

        public static TransportErrorCode fromValue(Long transportErrorCode) {
            for (TransportErrorCode code: values()) {
                if (code.value == transportErrorCode) {
                    return code;
                }
            }
            return null;
        }
    }
}

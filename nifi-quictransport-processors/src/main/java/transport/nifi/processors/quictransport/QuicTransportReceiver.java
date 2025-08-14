/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package transport.nifi.processors.quictransport;

import org.apache.nifi.annotation.behavior.ReadsAttribute;
import org.apache.nifi.annotation.behavior.ReadsAttributes;
import org.apache.nifi.annotation.behavior.WritesAttribute;
import org.apache.nifi.annotation.behavior.WritesAttributes;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.SeeAlso;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.*;
import org.apache.nifi.processor.util.StandardValidators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@Tags({"quic", "posthttp", "postquic", "transport", "receiver"})
@CapabilityDescription("Used to receive data from a QuicTransportSender using the QUIC protocol.")
@SeeAlso({})
@ReadsAttributes({@ReadsAttribute(attribute="", description="")})
@WritesAttributes({@WritesAttribute(attribute="", description="")})
public class QuicTransportReceiver extends AbstractProcessor {

    private static final Logger logger = LoggerFactory.getLogger(QuicTransportReceiver.class);

    private QuicServer qts = null;
    private static final boolean LOG_PACKETS = false;

    public static final PropertyDescriptor CERT_PATH = new PropertyDescriptor
            .Builder().name("CERT_PATH")
            .displayName("Cert Path")
            .description("Path to server cert in pem format.")
            .required(true)
            .defaultValue("/opt/tls/server.crt")
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor KEY_PATH = new PropertyDescriptor
            .Builder().name("KEY_PATH")
            .displayName("Key Path")
            .description("Path to server key unencrypted in pem format.")
            .required(true)
            .defaultValue("/opt/tls/server.key")
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor PORT = new PropertyDescriptor
            .Builder().name("PORT")
            .displayName("Endpoint Port")
            .description("Port the receiver is listening on. e.g. 8888")
            .required(true)
            .addValidator(StandardValidators.PORT_VALIDATOR)
            .build();

    public static final PropertyDescriptor PROTOCOL = new PropertyDescriptor
            .Builder().name("PROTOCOL")
            .displayName("QUIC Protocol")
            .description("Must be the same on the sender and receiver.")
            .required(true)
            .defaultValue("quicnifiv1")
            .addValidator(StandardValidators.CHARACTER_SET_VALIDATOR)
            .build();

    public static final Relationship SUCCESS = new Relationship.Builder()
            .name("Success")
            .description("Received FlowFiles are routed here.")
            .build();

    private List<PropertyDescriptor> descriptors;

    private Set<Relationship> relationships;

    @Override
    protected void init(final ProcessorInitializationContext context) {
        descriptors = new ArrayList<>();
        descriptors.add(PORT);
        descriptors.add(PROTOCOL);
        descriptors.add(CERT_PATH);
        descriptors.add(KEY_PATH);
        descriptors = Collections.unmodifiableList(descriptors);

        relationships = new HashSet<>();
        relationships.add(SUCCESS);
        relationships = Collections.unmodifiableSet(relationships);
    }

    @Override
    public Set<Relationship> getRelationships() {
        return this.relationships;
    }

    @Override
    public final List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return descriptors;
    }

    @OnScheduled
    public void onScheduled(final ProcessContext context) {
        if(this.qts == null){
            logger.info("Quic server null. Trying to initialise.");
            int port = context.getProperty(PORT).asInteger();
            String proto = context.getProperty(PROTOCOL).getValue();
            String certPath = context.getProperty(CERT_PATH).getValue();
            String keyPath = context.getProperty(KEY_PATH).getValue();

            this.qts = new QuicServer(certPath, keyPath, port, proto, LOG_PACKETS);
            try {
                this.qts.init();
                logger.info("Quic server initialised.");
            } catch (Exception exc){
                logger.warn("Exception thrown trying to init server client. " + exc.getMessage());
            }
        }
    }

    // TODO
    // Change server to be passed callback func from here for new thread

    @Override
    public void onTrigger(final ProcessContext context, final ProcessSession session) {
        FlowFile flowFile = session.get();
        if (flowFile == null) {
            return;
        }
        // TODO implement
    }
}

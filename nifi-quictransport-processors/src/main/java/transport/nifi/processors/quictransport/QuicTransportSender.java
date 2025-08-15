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

import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.annotation.behavior.ReadsAttribute;
import org.apache.nifi.annotation.behavior.ReadsAttributes;
import org.apache.nifi.annotation.behavior.WritesAttribute;
import org.apache.nifi.annotation.behavior.WritesAttributes;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.SeeAlso;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.util.StandardValidators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Tags({"quic", "posthttp", "postquic", "transport", "sender"})
@CapabilityDescription("Used to send data to a QuicTransportReceiver using the QUIC protocol.")
@SeeAlso({})
@ReadsAttributes({@ReadsAttribute(attribute="", description="")})
@WritesAttributes({@WritesAttribute(attribute="", description="")})
public class QuicTransportSender extends AbstractProcessor {

    private static final Logger logger = LoggerFactory.getLogger(QuicTransportSender.class);

    //private QuicClient qtc = null;
    private static final boolean LOG_PACKETS = false;

    public static final PropertyDescriptor URI = new PropertyDescriptor
            .Builder().name("URI")
            .displayName("URI Endpoint")
            .description("URI for the receiver to. e.g. localhost")
            .required(true)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor PORT = new PropertyDescriptor
            .Builder().name("PORT")
            .displayName("Endpoint Port")
            .description("Port the receiver is listening on. e.g. 8888")
            .required(true)
            .addValidator(StandardValidators.PORT_VALIDATOR)
            .build();

    public static final PropertyDescriptor CERT_CHECK = new PropertyDescriptor
            .Builder().name("CERT_CHECK")
            .displayName("Hostname Certificate Checking")
            .description("QUIC has built in tls. Disabling hostname validation can be useful for testing.")
            .required(true)
            .allowableValues("true", "false")
            .defaultValue("true")
            .addValidator(StandardValidators.BOOLEAN_VALIDATOR)
            .build();

    public static final PropertyDescriptor PROTOCOL = new PropertyDescriptor
            .Builder().name("PROTOCOL")
            .displayName("QUIC Protocol")
            .description("Must be the same on the sender and receiver.")
            .required(true)
            .defaultValue("quicnifiv1")
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor MTU = new PropertyDescriptor
            .Builder().name("MTU")
            .displayName("MTU / Packet Size")
            .description("-1 will use default. Value should be as high as the destination allows e.g. 8400.")
            .required(true)
            .defaultValue("-1")
            .addValidator(StandardValidators.INTEGER_VALIDATOR)
            .build();

    public static final PropertyDescriptor BATCH_SIZE = new PropertyDescriptor
            .Builder().name("BATCH_SIZE")
            .displayName("Batch Size")
            .description("Number of files to send in a single connection. This should be changed to size.")
            .required(true)
            .defaultValue("100")
            .addValidator(StandardValidators.INTEGER_VALIDATOR)
            .build();

    public static final Relationship SUCCESS = new Relationship.Builder()
            .name("Success")
            .description("Files which have been sent to a receiver are routed to this relationship.")
            .build();

    public static final Relationship FAILURE = new Relationship.Builder()
            .name("Failure")
            .description("Files which failed to send are routed to this relationship.")
            .build();

    private List<PropertyDescriptor> descriptors;

    private Set<Relationship> relationships;

    @Override
    protected void init(final ProcessorInitializationContext context) {
        descriptors = new ArrayList<>();
        descriptors.add(URI);
        descriptors.add(PORT);
        descriptors.add(MTU);
        descriptors.add(CERT_CHECK);
        descriptors.add(PROTOCOL);
        descriptors.add(BATCH_SIZE);
        descriptors = Collections.unmodifiableList(descriptors);

        relationships = new HashSet<>();
        relationships.add(SUCCESS);
        relationships.add(FAILURE);
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

    private QuicClient buildQt(final ProcessContext context){
        logger.info("Quic client null. Trying to initialise.");
        String uri = context.getProperty(URI).getValue();
        int port = context.getProperty(PORT).asInteger();
        int mtu = context.getProperty(MTU).asInteger();
        String proto = context.getProperty(PROTOCOL).getValue();
        boolean certCheck = context.getProperty(CERT_CHECK).asBoolean();
        QuicClient qt = new QuicClient(uri, port, proto, LOG_PACKETS, certCheck, mtu, logger);
        try {
            qt.init();
            logger.info("Quic client initialised.");
            return qt;
        } catch (Exception exc){
            logger.warn("Exception thrown trying to init quic client. " + exc.getMessage());
        }
        return null;
    }

    @OnScheduled
    public void onScheduled(final ProcessContext context) {
        //if(this.qtc == null){
            // Till i fix threading
            //this.qtc = buildQt(context);
        //}
    }

    @Override
    public void onTrigger(final ProcessContext context, final ProcessSession session) {
        final int batchSize = context.getProperty(BATCH_SIZE).asInteger();
        List<FlowFile> flowFiles = session.get(batchSize);
        List<FlowFile> success = new ArrayList<>();
        List<FlowFile> failures = new ArrayList<>();
        if (flowFiles.isEmpty()) {
            return;
        }
        // Threading isn't working as expected and the connections seem to be cheap.
        QuicClient qt = buildQt(context);
        // TODO need to report which ones were successful from send
        try {
            qt.send(flowFiles, session, success, failures);
        } catch (IOException e) {
            logger.error("Exception in QuicTransportSender onTrigger: " + e);
        }
        //session.transfer(success, SUCCESS);
        session.transfer(failures, FAILURE);
    }
}

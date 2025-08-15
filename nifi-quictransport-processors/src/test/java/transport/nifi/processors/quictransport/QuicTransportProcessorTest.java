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

import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.*;

public class QuicTransportProcessorTest {

    private TestRunner testSender, testReceiver;
    private String testPort = "8889";
    private String cert = "";
    private String key = "";

    @BeforeEach
    public void init() throws URISyntaxException {
        testSender = TestRunners.newTestRunner(QuicTransportSender.class);
        testReceiver = TestRunners.newTestRunner(QuicTransportReceiver.class);
        testReceiver.setAllowSynchronousSessionCommits(true);
        cert = Paths.get(
                getClass().getClassLoader().getResource("server.crt").toURI()).toString();
        key = Paths.get(
                getClass().getClassLoader().getResource("server.key").toURI()).toString();
    }

    @Test
    public void testSendSingleFile() throws InterruptedException, IOException {
        testSender.setProperty(QuicTransportSender.URI, "localhost");
        testSender.setProperty(QuicTransportSender.PORT, testPort);
        testSender.setProperty(QuicTransportSender.CERT_CHECK, "false");

        testReceiver.setProperty(QuicTransportReceiver.PORT, testPort);
        testReceiver.setProperty(QuicTransportReceiver.KEY_PATH, key);
        testReceiver.setProperty(QuicTransportReceiver.CERT_PATH, cert);

        testReceiver.run(1, false);

        MockFlowFile ff = new MockFlowFile(12323);
        byte[] dataToSend = "Test data string 12345!@!#$!".getBytes(StandardCharsets.UTF_8);
        Map<String, String> dummyAttrs = new HashMap<>();
        dummyAttrs.put("test1key", "test1val");
        dummyAttrs.put("test2key", "test2val");
        ff.setData(dataToSend);
        ff.putAttributes(dummyAttrs);

        testSender.enqueue(ff);
        testSender.run();

//        List<MockFlowFile> outputFlowFiles = testReceiver.getFlowFilesForRelationship(QuicTransportReceiver.SUCCESS);
//

        long responseTimeout = 600;

        long startTime = System.currentTimeMillis();
        while ((System.currentTimeMillis() - startTime < responseTimeout)) {
            Thread.sleep(100);
        }
//        Map<String, String> outputMap = testReceiver.getFlowFilesForRelationship(QuicTransportReceiver.SUCCESS).get(0).getAttributes();
//        testReceiver.getFlowFilesForRelationship(QuicTransportReceiver.SUCCESS).get(0)
//                .assertContentEquals(dataToSend);
//        testSender.getFlowFilesForRelationship(QuicTransportSender.SUCCESS).get(0)
//                .assertContentEquals(dataToSend);
//        Assertions.assertEquals(0, testSender.getFlowFilesForRelationship(QuicTransportSender.FAILURE).size());
//        for(String key : dummyAttrs.keySet()){
//            Assertions.assertEquals(dummyAttrs.get(key), outputMap.get(key));
//        }
    }

    @Test
    public void testSendMultiFile() throws InterruptedException, IOException {
        testSender.setProperty(QuicTransportSender.URI, "localhost");
        testSender.setProperty(QuicTransportSender.PORT, testPort);
        testSender.setProperty(QuicTransportSender.CERT_CHECK, "false");

        testReceiver.setProperty(QuicTransportReceiver.PORT, testPort);
        testReceiver.setProperty(QuicTransportReceiver.KEY_PATH, key);
        testReceiver.setProperty(QuicTransportReceiver.CERT_PATH, cert);

        testReceiver.run(1, false);
        byte[] dataToSend = "Test data string 12345!@!#$!".getBytes(StandardCharsets.UTF_8);
        Map<String, String> dummyAttrs = new HashMap<>();
        dummyAttrs.put("test1key", "test1val");
        dummyAttrs.put("test2key", "test2val");
        for(int i = 0; i <  10; i++){
            MockFlowFile ff = new MockFlowFile(i);
            ff.setData(dataToSend);
            ff.putAttributes(dummyAttrs);
            testSender.enqueue(ff);
        }

        testSender.run();

//        List<MockFlowFile> outputFlowFiles = testReceiver.getFlowFilesForRelationship(QuicTransportReceiver.SUCCESS);
//

        long responseTimeout = 400;

        long startTime = System.currentTimeMillis();
        while ((System.currentTimeMillis() - startTime < responseTimeout)) {

            Thread.sleep(100);
        }
        Map<String, String> outputMap = testReceiver.getFlowFilesForRelationship(QuicTransportReceiver.SUCCESS).get(6).getAttributes();
        testReceiver.getFlowFilesForRelationship(QuicTransportReceiver.SUCCESS).get(6)
                .assertContentEquals(dataToSend);
        testSender.getFlowFilesForRelationship(QuicTransportSender.SUCCESS).get(6)
                .assertContentEquals(dataToSend);
        Assertions.assertEquals(0, testSender.getFlowFilesForRelationship(QuicTransportSender.FAILURE).size());
        for(String key : dummyAttrs.keySet()){
            Assertions.assertEquals(dummyAttrs.get(key), outputMap.get(key));
        }
    }

}

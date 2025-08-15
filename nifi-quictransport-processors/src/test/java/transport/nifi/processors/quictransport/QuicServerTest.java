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

import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.validation.constraints.AssertTrue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

public class QuicServerTest {

    private TestRunner testRunner;

    @BeforeEach
    public void init() {
        testRunner = TestRunners.newTestRunner(QuicTransportSender.class);
    }

    @Test
    public void testServerStartup() throws Exception {
//        int testPort = 8881;
//        String cert = Paths.get(
//                getClass().getClassLoader().getResource("server.crt").toURI()).toString();
//        String key = Paths.get(
//                getClass().getClassLoader().getResource("server.key").toURI()).toString();
//        QuicServer server = new QuicServer(cert,
//                key, testPort,
//                "testProto", false);
//        server.init();
//        server.stop();
    }

    // Moved flowfile logic into the QuicServer - this no longer makes sense to test. Just using full processor tests
//    @Test
//    public void testServerSend() throws Exception {
//        int testPort = 8881;
//        String protoName = "testProto";
//        String cert = Paths.get(
//                getClass().getClassLoader().getResource("server.crt").toURI()).toString();
//        String key = Paths.get(
//                getClass().getClassLoader().getResource("server.key").toURI()).toString();
//        QuicServer server = new QuicServer(cert,
//                key, testPort,protoName
//                , false);
//        server.init();
//
//        QuicClient client = new QuicClient("localhost", testPort, protoName, false, false);
//
//        String dataToSend = "TestData blah";
//        client.init();
//        for(int i = 0; i < 100; i++){
//            try {
//                client.send(dataToSend.getBytes(StandardCharsets.UTF_8));
//            } catch (IOException exc){
//                Assertions.fail("IO Exception caught sending data " + exc.getMessage());
//            }
//        }
//
//        server.stop();
//    }


}

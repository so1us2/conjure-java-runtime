/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.conjure.java.client.jaxrs;

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.dialogue.serde.DefaultConjureRuntime;
import com.palantir.conjure.java.okhttp.NoOpHostEventsSink;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.hc4.ApacheHttpClientChannels;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import java.util.Arrays;
import java.util.Collection;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public final class JaxRsClientPathParamHandlingTest extends TestBase {

    @Parameterized.Parameters(name = "Client: {0}")
    public static Collection<Object[]> clients() {
        return Arrays.asList(new Object[][] {{Client.CJR}, {Client.DIALOGUE}});
    }

    public enum Client {
        CJR,
        DIALOGUE;

        <T> T create(Class<T> service, ClientConfiguration clientConfiguration) {
            switch (this) {
                case CJR:
                    return JaxRsClient.create(service, AGENT, NoOpHostEventsSink.INSTANCE, clientConfiguration);
                case DIALOGUE:
                    Channel channel = ApacheHttpClientChannels.create(clientConfiguration);
                    return JaxRsClient.create(
                            service, channel, DefaultConjureRuntime.builder().build());
            }
            throw new SafeIllegalStateException("Unknown client", SafeArg.of("client", this));
        }
    }

    @Parameterized.Parameter
    public Client clientFactory;

    @Rule
    public final MockWebServer server = new MockWebServer();

    private Service client;

    @Before
    public void before() {
        client = clientFactory.create(Service.class, createTestConfig("http://localhost:" + server.getPort()));
        MockResponse mockResponse = new MockResponse().setResponseCode(200);
        server.enqueue(mockResponse);
    }

    @Path("/")
    public interface Service {

        @GET
        @Path("complex/{path:.*}")
        void complexPath(@PathParam("path") String path);

        @GET
        @Path("begin/{path}/end")
        void innerPath(@PathParam("path") String path);
    }

    @Test
    public void wildcardPathParameterSlashesAreEncoded() throws Exception {
        client.complexPath("foo/bar");
        RecordedRequest takeRequest = server.takeRequest();
        assertThat(takeRequest.getRequestLine()).isEqualTo("GET /complex/foo%2Fbar HTTP/1.1");
    }

    @Test
    public void wildcardPathParameterAllowsEmptyString() throws Exception {
        client.complexPath("");
        RecordedRequest takeRequest = server.takeRequest();
        assertThat(takeRequest.getRequestLine()).isEqualTo("GET /complex/ HTTP/1.1");
    }

    @Test
    public void innerPathParameterAllowsEmptyString() throws Exception {
        client.innerPath("");
        RecordedRequest takeRequest = server.takeRequest();
        assertThat(takeRequest.getRequestLine()).isEqualTo("GET /begin//end HTTP/1.1");
    }
}

/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.hc.core5.testing.nio;

import static org.hamcrest.MatcherAssert.assertThat;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.ContentLengthStrategy;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.MalformedChunkCodingException;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.BasicHttpTransportMetrics;
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
import org.apache.hc.core5.http.impl.Http1StreamListener;
import org.apache.hc.core5.http.impl.HttpProcessors;
import org.apache.hc.core5.http.impl.nio.AbstractContentEncoder;
import org.apache.hc.core5.http.impl.nio.AbstractMessageWriter;
import org.apache.hc.core5.http.impl.nio.DefaultHttpRequestFactory;
import org.apache.hc.core5.http.impl.nio.DefaultHttpRequestParser;
import org.apache.hc.core5.http.impl.nio.ServerHttp1StreamDuplexer;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.message.BasicLineFormatter;
import org.apache.hc.core5.http.message.LineFormatter;
import org.apache.hc.core5.http.message.RequestLine;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.AsyncRequestConsumer;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.AsyncResponseProducer;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncServerRequestHandler;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.ContentEncoder;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.nio.NHttpMessageParser;
import org.apache.hc.core5.http.nio.NHttpMessageWriter;
import org.apache.hc.core5.http.nio.RequestChannel;
import org.apache.hc.core5.http.nio.ResponseChannel;
import org.apache.hc.core5.http.nio.SessionOutputBuffer;
import org.apache.hc.core5.http.nio.entity.AsyncEntityProducers;
import org.apache.hc.core5.http.nio.entity.BasicAsyncEntityProducer;
import org.apache.hc.core5.http.nio.entity.DigestingEntityConsumer;
import org.apache.hc.core5.http.nio.entity.DigestingEntityProducer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityProducer;
import org.apache.hc.core5.http.nio.support.AbstractServerExchangeHandler;
import org.apache.hc.core5.http.nio.support.AsyncRequestBuilder;
import org.apache.hc.core5.http.nio.support.BasicAsyncServerExpectationDecorator;
import org.apache.hc.core5.http.nio.support.BasicRequestConsumer;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.http.nio.support.BasicResponseProducer;
import org.apache.hc.core5.http.nio.support.ImmediateResponseExchangeHandler;
import org.apache.hc.core5.http.nio.support.classic.AbstractClassicEntityConsumer;
import org.apache.hc.core5.http.nio.support.classic.AbstractClassicEntityProducer;
import org.apache.hc.core5.http.nio.support.classic.AbstractClassicServerExchangeHandler;
import org.apache.hc.core5.http.protocol.DefaultHttpProcessor;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.RequestConnControl;
import org.apache.hc.core5.http.protocol.RequestContent;
import org.apache.hc.core5.http.protocol.RequestTargetHost;
import org.apache.hc.core5.http.protocol.RequestValidateHost;
import org.apache.hc.core5.http.support.BasicRequestBuilder;
import org.apache.hc.core5.http.support.BasicResponseBuilder;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.testing.SSLTestContexts;
import org.apache.hc.core5.testing.extension.ExecutorResource;
import org.apache.hc.core5.testing.extension.nio.Http1TestResources;
import org.apache.hc.core5.util.CharArrayBuffer;
import org.apache.hc.core5.util.TextUtils;
import org.apache.hc.core5.util.Timeout;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

abstract class Http1IntegrationTest {

    private static final Timeout TIMEOUT = Timeout.ofMinutes(1);
    private static final Timeout LONG_TIMEOUT = Timeout.ofMinutes(2);

    private final URIScheme scheme;
    @RegisterExtension
    private final Http1TestResources resources;
    @RegisterExtension
    private final ExecutorResource executorResource;

    public Http1IntegrationTest(final URIScheme scheme) {
        this.scheme = scheme;
        this.resources = new Http1TestResources(scheme, TIMEOUT);
        this.executorResource = new ExecutorResource(5);
    }

    private HttpHost target(final InetSocketAddress serverEndpoint) {
        return new HttpHost(scheme.id, null, "localhost", serverEndpoint.getPort());
    }

    @Test
    void testSimpleGet() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

        server.register("/hello", () -> new SingleLineResponseHandler("Hi there"));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        for (int i = 0; i < 5; i++) {
            final BasicHttpRequest request = BasicRequestBuilder.get()
                    .setHttpHost(target)
                    .setPath("/hello")
                    .build();
            final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                    new BasicRequestProducer(request, null),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
            final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assertions.assertNotNull(result);
            final HttpResponse response1 = result.getHead();
            final String entity1 = result.getBody();
            Assertions.assertNotNull(response1);
            Assertions.assertEquals(200, response1.getCode());
            Assertions.assertEquals("Hi there", entity1);
        }
    }

    @Test
    void testSimpleGetConnectionClose() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

        server.register("/hello", () -> new SingleLineResponseHandler("Hi there"));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        for (int i = 0; i < 5; i++) {
            final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
            try (final ClientSessionEndpoint streamEndpoint = connectFuture.get()) {
                final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                        AsyncRequestBuilder.get()
                                .setHttpHost(target)
                                .setPath("/hello")
                                .addHeader(HttpHeaders.CONNECTION, "close")
                                .build(),
                        new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
                final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
                Assertions.assertNotNull(result);
                final HttpResponse response1 = result.getHead();
                final String entity1 = result.getBody();
                Assertions.assertNotNull(response1);
                Assertions.assertEquals(200, response1.getCode());
                Assertions.assertEquals("Hi there", entity1);
            }
        }
    }

    @Test
    void testSimpleGetIdentityTransfer() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

        server.register("/hello", () -> new SingleLineResponseHandler("Hi there"));
        server.configure(new DefaultHttpProcessor(new RequestValidateHost()));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();

        final int reqNo = 5;

        for (int i = 0; i < reqNo; i++) {
            final Future<ClientSessionEndpoint> connectFuture = client.connect("localhost", serverEndpoint.getPort(), TIMEOUT);
            final ClientSessionEndpoint streamEndpoint = connectFuture.get();

            final BasicHttpRequest request = BasicRequestBuilder.get()
                    .setHttpHost(target)
                    .setPath("/hello")
                    .build();
            final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                    new BasicRequestProducer(request, null),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
            final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());

            streamEndpoint.close();

            Assertions.assertNotNull(result);
            final HttpResponse response = result.getHead();
            final String entity = result.getBody();
            Assertions.assertNotNull(response);
            Assertions.assertEquals(200, response.getCode());
            Assertions.assertEquals("Hi there", entity);
        }

    }

    @Test
    void testPostIdentityTransfer() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

        server.register("/hello", () -> new SingleLineResponseHandler("Hi there"));
        server.configure(new DefaultHttpProcessor(new RequestValidateHost()));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();

        final int reqNo = 5;

        for (int i = 0; i < reqNo; i++) {
            final Future<ClientSessionEndpoint> connectFuture = client.connect("localhost", serverEndpoint.getPort(), TIMEOUT);
            final ClientSessionEndpoint streamEndpoint = connectFuture.get();

            final BasicHttpRequest request = BasicRequestBuilder.post()
                    .setHttpHost(target)
                    .setPath("/hello")
                    .build();
            final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                    new BasicRequestProducer(request, new MultiLineEntityProducer("Hello", 16 * i)),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
            final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());

            streamEndpoint.close();

            Assertions.assertNotNull(result);
            final HttpResponse response = result.getHead();
            final String entity = result.getBody();
            Assertions.assertNotNull(response);
            Assertions.assertEquals(200, response.getCode());
            Assertions.assertEquals("Hi there", entity);
        }
    }

    @Test
    void testPostIdentityTransferOutOfSequenceResponseNotOK() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

        server.register("/hello", () -> new ImmediateResponseExchangeHandler(500, "Go away"));
        server.configure(new DefaultHttpProcessor(new RequestValidateHost()));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();

        final int reqNo = 5;

        for (int i = 0; i < reqNo; i++) {
            final Future<ClientSessionEndpoint> connectFuture = client.connect("localhost", serverEndpoint.getPort(), TIMEOUT);
            final ClientSessionEndpoint streamEndpoint = connectFuture.get();

            final BasicHttpRequest request = BasicRequestBuilder.post()
                    .setHttpHost(target)
                    .setPath("/hello")
                    .build();
            final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                    new BasicRequestProducer(request, new MultiLineEntityProducer("Hello", 16 * i)),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
            final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());

            streamEndpoint.close();

            Assertions.assertNotNull(result);
            final HttpResponse response = result.getHead();
            final String entity = result.getBody();
            Assertions.assertNotNull(response);
            Assertions.assertEquals(500, response.getCode());
            Assertions.assertEquals("Go away", entity);
        }
    }

    @Test
    void testPostOutOfSequenceResponseOK() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

        server.register("/hello", () -> new ImmediateResponseExchangeHandler(200, "Welcome"));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();

        final int reqNo = 5;

        final Future<ClientSessionEndpoint> connectFuture = client.connect("localhost", serverEndpoint.getPort(), TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        for (int i = 0; i < reqNo; i++) {
            final BasicHttpRequest request = BasicRequestBuilder.post()
                    .setHttpHost(target)
                    .setPath("/hello")
                    .build();
            final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                    new BasicRequestProducer(request, new MultiLineEntityProducer("Hello", 512 * i)),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
            final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());

            Assertions.assertNotNull(result);
            final HttpResponse response = result.getHead();
            final String entity = result.getBody();
            Assertions.assertNotNull(response);
            Assertions.assertEquals(200, response.getCode());
            Assertions.assertEquals("Welcome", entity);
        }
    }

    @Test
    void testPostOutOfSequenceResponseOKConnectionClose() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

        server.register("/hello", () -> new ImmediateResponseExchangeHandler(
                BasicResponseBuilder.create(200)
                        .addHeader(HttpHeaders.CONNECTION, "Close")
                        .build(),
                "Welcome"));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();

        final int reqNo = 5;

        for (int i = 0; i < reqNo; i++) {
            final Future<ClientSessionEndpoint> connectFuture = client.connect("localhost", serverEndpoint.getPort(), TIMEOUT);
            final ClientSessionEndpoint streamEndpoint = connectFuture.get();

            final BasicHttpRequest request = BasicRequestBuilder.post()
                    .setHttpHost(target)
                    .setPath("/hello")
                    .build();
            final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                    new BasicRequestProducer(request, new MultiLineEntityProducer("Hello", 512 * i)),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
            final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());

            streamEndpoint.close();

            Assertions.assertNotNull(result);
            final HttpResponse response = result.getHead();
            final String entity = result.getBody();
            Assertions.assertNotNull(response);
            Assertions.assertEquals(200, response.getCode());
            Assertions.assertEquals("Welcome", entity);
        }
    }

    @Test
    void testSimpleGetsPipelined() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

        server.register("/hello", () -> new SingleLineResponseHandler("Hi there"));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final Queue<Future<Message<HttpResponse, String>>> queue = new LinkedList<>();
        for (int i = 0; i < 5; i++) {
            final BasicHttpRequest request = BasicRequestBuilder.get()
                    .setHttpHost(target)
                    .setPath("/hello")
                    .build();
            queue.add(streamEndpoint.execute(
                    new BasicRequestProducer(request, null),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null));
        }
        while (!queue.isEmpty()) {
            final Future<Message<HttpResponse, String>> future = queue.remove();
            final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assertions.assertNotNull(result);
            final HttpResponse response = result.getHead();
            final String entity = result.getBody();
            Assertions.assertNotNull(response);
            Assertions.assertEquals(200, response.getCode());
            Assertions.assertEquals("Hi there", entity);
        }
    }

    @Test
    void testLargeGet() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

        server.register("/", () -> new MultiLineResponseHandler("0123456789abcdef", 5000));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final BasicHttpRequest request1 = BasicRequestBuilder.get()
                .setHttpHost(target)
                .setPath("/")
                .build();
        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                new BasicRequestProducer(request1, null),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);

        final Message<HttpResponse, String> result1 = future1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(result1);
        final HttpResponse response1 = result1.getHead();
        Assertions.assertNotNull(response1);
        Assertions.assertEquals(200, response1.getCode());
        final String s1 = result1.getBody();
        Assertions.assertNotNull(s1);
        final StringTokenizer t1 = new StringTokenizer(s1, "\r\n");
        while (t1.hasMoreTokens()) {
            Assertions.assertEquals("0123456789abcdef", t1.nextToken());
        }

        final BasicHttpRequest request2 = BasicRequestBuilder.get()
                .setHttpHost(target)
                .setPath("/")
                .build();
        final Future<Message<HttpResponse, String>> future2 = streamEndpoint.execute(
                new BasicRequestProducer(request2, null),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer(512)), null);

        final Message<HttpResponse, String> result2 = future2.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(result2);
        final HttpResponse response2 = result2.getHead();
        Assertions.assertNotNull(response2);
        Assertions.assertEquals(200, response2.getCode());
        final String s2 = result2.getBody();
        Assertions.assertNotNull(s2);
        final StringTokenizer t2 = new StringTokenizer(s2, "\r\n");
        while (t2.hasMoreTokens()) {
            Assertions.assertEquals("0123456789abcdef", t2.nextToken());
        }
    }

    @Test
    void testLargeGetsPipelined() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

        server.register("/", () -> new MultiLineResponseHandler("0123456789abcdef", 2000));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final Queue<Future<Message<HttpResponse, String>>> queue = new LinkedList<>();
        for (int i = 0; i < 5; i++) {
            final BasicHttpRequest request = BasicRequestBuilder.get()
                    .setHttpHost(target)
                    .setPath("/")
                    .build();
            queue.add(streamEndpoint.execute(
                    new BasicRequestProducer(request, null),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null));
        }
        while (!queue.isEmpty()) {
            final Future<Message<HttpResponse, String>> future = queue.remove();
            final Message<HttpResponse, String> result = future.get(LONG_TIMEOUT.getDuration(), LONG_TIMEOUT.getTimeUnit());
            Assertions.assertNotNull(result);
            final HttpResponse response = result.getHead();
            Assertions.assertNotNull(response);
            Assertions.assertEquals(200, response.getCode());
            final String entity = result.getBody();
            Assertions.assertNotNull(entity);
            final StringTokenizer t = new StringTokenizer(entity, "\r\n");
            while (t.hasMoreTokens()) {
                Assertions.assertEquals("0123456789abcdef", t.nextToken());
            }
        }
    }

    @Test
    void testBasicPost() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

        server.register("/hello", () -> new SingleLineResponseHandler("Hi back"));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        for (int i = 0; i < 5; i++) {
            final BasicHttpRequest request = BasicRequestBuilder.post()
                    .setHttpHost(target)
                    .setPath("/hello")
                    .build();
            final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                    new BasicRequestProducer(request, AsyncEntityProducers.create("Hi there")),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
            final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assertions.assertNotNull(result);
            final HttpResponse response1 = result.getHead();
            final String entity1 = result.getBody();
            Assertions.assertNotNull(response1);
            Assertions.assertEquals(200, response1.getCode());
            Assertions.assertEquals("Hi back", entity1);
        }
    }

    @Test
    void testBasicPostPipelined() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

        server.register("/hello", () -> new SingleLineResponseHandler("Hi back"));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final Queue<Future<Message<HttpResponse, String>>> queue = new LinkedList<>();
        for (int i = 0; i < 5; i++) {
            final BasicHttpRequest request = BasicRequestBuilder.post()
                    .setHttpHost(target)
                    .setPath("/hello")
                    .build();
            queue.add(streamEndpoint.execute(
                    new BasicRequestProducer(request, AsyncEntityProducers.create("Hi there")),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null));
        }
        while (!queue.isEmpty()) {
            final Future<Message<HttpResponse, String>> future = queue.remove();
            final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assertions.assertNotNull(result);
            final HttpResponse response = result.getHead();
            final String entity = result.getBody();
            Assertions.assertNotNull(response);
            Assertions.assertEquals(200, response.getCode());
            Assertions.assertEquals("Hi back", entity);
        }
    }

    @Test
    void testHttp10Post() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

        server.register("/hello", () -> new SingleLineResponseHandler("Hi back"));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        for (int i = 0; i < 5; i++) {
            final BasicHttpRequest request = BasicRequestBuilder.post()
                    .setHttpHost(target)
                    .setPath("/hello")
                    .build();
            request.setVersion(HttpVersion.HTTP_1_0);
            final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                    new BasicRequestProducer(request, AsyncEntityProducers.create("Hi there")),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
            final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assertions.assertNotNull(result);
            final HttpResponse response1 = result.getHead();
            final String entity1 = result.getBody();
            Assertions.assertNotNull(response1);
            Assertions.assertEquals(200, response1.getCode());
            Assertions.assertEquals("Hi back", entity1);
        }
    }

    @Test
    void testHTTP11FeaturesDisabledWithHTTP10Requests() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

        server.register("/hello", () -> new SingleLineResponseHandler("Hi back"));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final BasicHttpRequest request = BasicRequestBuilder.post()
                .setHttpHost(target)
                .setPath("/hello")
                .build();
        request.setVersion(HttpVersion.HTTP_1_0);
        final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                new BasicRequestProducer(request, new BasicAsyncEntityProducer(new byte[] {'a', 'b', 'c'}, null, true)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final ExecutionException exception = Assertions.assertThrows(ExecutionException.class, future::get);
        Assertions.assertInstanceOf(ProtocolException.class, exception.getCause());
    }

    @Test
    void testNoEntityPost() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

        server.register("/hello", () -> new SingleLineResponseHandler("Hi back"));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        for (int i = 0; i < 5; i++) {
            final BasicHttpRequest request = BasicRequestBuilder.post()
                    .setHttpHost(target)
                    .setPath("/hello")
                    .build();
            final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                    new BasicRequestProducer(request, null),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
            final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assertions.assertNotNull(result);
            final HttpResponse response1 = result.getHead();
            final String entity1 = result.getBody();
            Assertions.assertNotNull(response1);
            Assertions.assertEquals(200, response1.getCode());
            Assertions.assertEquals("Hi back", entity1);
        }
    }

    @Test
    void testLargePost() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

        server.register("*", () -> new EchoHandler(2048));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        for (int i = 0; i < 5; i++) {
            final BasicHttpRequest request = BasicRequestBuilder.post()
                    .setHttpHost(target)
                    .setPath("/echo")
                    .build();
            final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                    new BasicRequestProducer(request, new MultiLineEntityProducer("0123456789abcdef", 5000)),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
            final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assertions.assertNotNull(result);
            final HttpResponse response = result.getHead();
            Assertions.assertNotNull(response);
            Assertions.assertEquals(200, response.getCode());
            final String entity = result.getBody();
            Assertions.assertNotNull(entity);
            final StringTokenizer t = new StringTokenizer(entity, "\r\n");
            while (t.hasMoreTokens()) {
                Assertions.assertEquals("0123456789abcdef", t.nextToken());
            }
        }
    }

    @Test
    void testPostsPipelinedLargeResponse() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

        server.register("/", () -> new MultiLineResponseHandler("0123456789abcdef", 2000));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final Queue<Future<Message<HttpResponse, String>>> queue = new LinkedList<>();
        for (int i = 0; i < 2; i++) {
            final BasicHttpRequest request = BasicRequestBuilder.post()
                    .setHttpHost(target)
                    .setPath("/")
                    .build();
            queue.add(streamEndpoint.execute(
                    new BasicRequestProducer(request, AsyncEntityProducers.create("Hi there")),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null));
        }
        while (!queue.isEmpty()) {
            final Future<Message<HttpResponse, String>> future = queue.remove();
            final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assertions.assertNotNull(result);
            final HttpResponse response = result.getHead();
            Assertions.assertNotNull(response);
            Assertions.assertEquals(200, response.getCode());
            final String entity = result.getBody();
            Assertions.assertNotNull(entity);
            final StringTokenizer t = new StringTokenizer(entity, "\r\n");
            while (t.hasMoreTokens()) {
                Assertions.assertEquals("0123456789abcdef", t.nextToken());
            }
        }
    }


    @Test
    void testLargePostsPipelined() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

        server.register("*", () -> new EchoHandler(2048));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final Queue<Future<Message<HttpResponse, String>>> queue = new LinkedList<>();
        for (int i = 0; i < 5; i++) {
            final BasicHttpRequest request = BasicRequestBuilder.post()
                    .setHttpHost(target)
                    .setPath("/echo")
                    .build();
            queue.add(streamEndpoint.execute(
                    new BasicRequestProducer(request, new MultiLineEntityProducer("0123456789abcdef", 5000)),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null));
        }
        while (!queue.isEmpty()) {
            final Future<Message<HttpResponse, String>> future = queue.remove();
            final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assertions.assertNotNull(result);
            final HttpResponse response = result.getHead();
            Assertions.assertNotNull(response);
            Assertions.assertEquals(200, response.getCode());
            final String entity = result.getBody();
            Assertions.assertNotNull(entity);
            final StringTokenizer t = new StringTokenizer(entity, "\r\n");
            while (t.hasMoreTokens()) {
                Assertions.assertEquals("0123456789abcdef", t.nextToken());
            }
        }
    }

    @Test
    void testSimpleHead() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

        server.register("/hello", () -> new SingleLineResponseHandler("Hi there"));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        for (int i = 0; i < 5; i++) {
            final BasicHttpRequest request = BasicRequestBuilder.head()
                    .setHttpHost(target)
                    .setPath("/hello")
                    .build();
            final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                    new BasicRequestProducer(request, null),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
            final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assertions.assertNotNull(result);
            final HttpResponse response1 = result.getHead();
            Assertions.assertNotNull(response1);
            Assertions.assertEquals(200, response1.getCode());
            Assertions.assertNull(result.getBody());
        }
    }

    @Test
    void testSimpleHeadConnectionClose() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

        server.register("/hello", () -> new SingleLineResponseHandler("Hi there"));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        for (int i = 0; i < 5; i++) {
            final Future<ClientSessionEndpoint> connectFuture = client.connect(
                    "localhost", serverEndpoint.getPort(), TIMEOUT);
            try (final ClientSessionEndpoint streamEndpoint = connectFuture.get()) {
                final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                        AsyncRequestBuilder.head()
                                .setHttpHost(target)
                                .setPath("/hello")
                                .addHeader(HttpHeaders.CONNECTION, "close")
                                .build(),
                        new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
                final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
                Assertions.assertNotNull(result);
                final HttpResponse response1 = result.getHead();
                Assertions.assertNotNull(response1);
                Assertions.assertEquals(200, response1.getCode());
                Assertions.assertNull(result.getBody());
            }
        }
    }

    @Test
    void testHeadPipelined() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

        server.register("/hello", () -> new SingleLineResponseHandler("Hi there"));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final Queue<Future<Message<HttpResponse, String>>> queue = new LinkedList<>();
        for (int i = 0; i < 5; i++) {
            final BasicHttpRequest request = BasicRequestBuilder.head()
                    .setHttpHost(target)
                    .setPath("/hello")
                    .build();
            queue.add(streamEndpoint.execute(
                    new BasicRequestProducer(request, null),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null));
        }
        while (!queue.isEmpty()) {
            final Future<Message<HttpResponse, String>> future = queue.remove();
            final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assertions.assertNotNull(result);
            final HttpResponse response1 = result.getHead();
            Assertions.assertNotNull(response1);
            Assertions.assertEquals(200, response1.getCode());
            Assertions.assertNull(result.getBody());
        }
    }

    @Test
    void testExpectationFailed() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

        server.register("*", () -> new MessageExchangeHandler<String>(new StringAsyncEntityConsumer()) {

            @Override
            protected void handle(
                    final Message<HttpRequest, String> request,
                    final AsyncServerRequestHandler.ResponseTrigger responseTrigger,
                    final HttpContext context) throws IOException, HttpException {
                responseTrigger.submitResponse(new BasicResponseProducer(HttpStatus.SC_OK, "All is well"), context);

            }
        });
        server.configure(handler -> new BasicAsyncServerExpectationDecorator(handler) {

            @Override
            protected AsyncResponseProducer verify(final HttpRequest request, final HttpContext context) throws IOException, HttpException {
                final Header h = request.getFirstHeader("password");
                if (h != null && "secret".equals(h.getValue())) {
                    return null;
                } else {
                    return new BasicResponseProducer(HttpStatus.SC_UNAUTHORIZED, "You shall not pass");
                }
            }
        });
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<IOSession> sessionFuture = client.requestSession(
                new HttpHost("localhost", serverEndpoint.getPort()), TIMEOUT, null);
        final IOSession ioSession = sessionFuture.get();
        try (final ClientSessionEndpoint streamEndpoint = new ClientSessionEndpoint(ioSession)) {

            final BasicHttpRequest request1 = BasicRequestBuilder.post()
                    .setHttpHost(target)
                    .setPath("/echo")
                    .addHeader("password", "secret")
                    .build();
            final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                    new BasicRequestProducer(request1, new MultiLineEntityProducer("0123456789abcdef", 1000)),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
            final Message<HttpResponse, String> result1 = future1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assertions.assertNotNull(result1);
            final HttpResponse response1 = result1.getHead();
            Assertions.assertNotNull(response1);
            Assertions.assertEquals(200, response1.getCode());
            Assertions.assertEquals("All is well", result1.getBody());

            Assertions.assertTrue(ioSession.isOpen());

            final BasicHttpRequest request2 = BasicRequestBuilder.post()
                    .setHttpHost(target)
                    .setPath("/echo")
                    .build();
            final Future<Message<HttpResponse, String>> future2 = streamEndpoint.execute(
                    new BasicRequestProducer(request2, new MultiLineEntityProducer("0123456789abcdef", 5000)),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
            final Message<HttpResponse, String> result2 = future2.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assertions.assertNotNull(result2);
            final HttpResponse response2 = result2.getHead();
            Assertions.assertNotNull(response2);
            Assertions.assertEquals(HttpStatus.SC_UNAUTHORIZED, response2.getCode());
            Assertions.assertEquals("You shall not pass", result2.getBody());

            Assertions.assertTrue(ioSession.isOpen());

            final BasicHttpRequest request3 = BasicRequestBuilder.post()
                    .setHttpHost(target)
                    .setPath("/echo")
                    .addHeader("password", "secret")
                    .build();
            final Future<Message<HttpResponse, String>> future3 = streamEndpoint.execute(
                    new BasicRequestProducer(request3, new MultiLineEntityProducer("0123456789abcdef", 1000)),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
            final Message<HttpResponse, String> result3 = future3.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assertions.assertNotNull(result3);
            final HttpResponse response3 = result3.getHead();
            Assertions.assertNotNull(response3);
            Assertions.assertEquals(200, response3.getCode());
            Assertions.assertEquals("All is well", result3.getBody());

            Assertions.assertTrue(ioSession.isOpen());

            final BasicHttpRequest request4 = BasicRequestBuilder.post()
                    .setHttpHost(target)
                    .setPath("/echo")
                    .build();
            final Future<Message<HttpResponse, String>> future4 = streamEndpoint.execute(
                    new BasicRequestProducer(request4, AsyncEntityProducers.create("blah")),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
            final Message<HttpResponse, String> result4 = future4.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assertions.assertNotNull(result4);
            final HttpResponse response4 = result4.getHead();
            Assertions.assertNotNull(response4);
            Assertions.assertEquals(HttpStatus.SC_UNAUTHORIZED, response4.getCode());
            Assertions.assertEquals("You shall not pass", result4.getBody());

            Assertions.assertFalse(ioSession.isOpen());
        }
    }

    @Test
    void testExpectationFailedCloseConnection() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

        server.register("*", () -> new MessageExchangeHandler<String>(new StringAsyncEntityConsumer()) {

            @Override
            protected void handle(
                    final Message<HttpRequest, String> request,
                    final AsyncServerRequestHandler.ResponseTrigger responseTrigger,
                    final HttpContext context) throws IOException, HttpException {
                responseTrigger.submitResponse(new BasicResponseProducer(HttpStatus.SC_OK, "All is well"), context);

            }
        });
        server.configure(handler -> new BasicAsyncServerExpectationDecorator(handler) {

            @Override
            protected AsyncResponseProducer verify(final HttpRequest request, final HttpContext context) throws IOException, HttpException {
                final Header h = request.getFirstHeader("password");
                if (h != null && "secret".equals(h.getValue())) {
                    return null;
                } else {
                    final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_UNAUTHORIZED);
                    response.addHeader(HttpHeaders.CONNECTION, HeaderElements.CLOSE);
                    return new BasicResponseProducer(response, "You shall not pass");
                }
            }
        });
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<IOSession> sessionFuture = client.requestSession(
                new HttpHost("localhost", serverEndpoint.getPort()), TIMEOUT, null);
        final IOSession ioSession = sessionFuture.get();
        try (final ClientSessionEndpoint streamEndpoint = new ClientSessionEndpoint(ioSession)) {

            final BasicHttpRequest request1 = BasicRequestBuilder.post()
                    .setHttpHost(target)
                    .setPath("/echo")
                    .build();
            final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                    new BasicRequestProducer(request1, new MultiBinEntityProducer(
                            new byte[] {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'},
                            100000,
                            ContentType.TEXT_PLAIN)),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
            final Message<HttpResponse, String> result1 = future1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assertions.assertNotNull(result1);
            final HttpResponse response1 = result1.getHead();
            Assertions.assertNotNull(response1);
            Assertions.assertEquals(HttpStatus.SC_UNAUTHORIZED, response1.getCode());
            Assertions.assertEquals("You shall not pass", result1.getBody());

            Assertions.assertFalse(streamEndpoint.isOpen());
        }
    }

    @Test
    void testDelayedExpectContinueAck() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

        // Disable 100-continue handshake on the server side
        server.configure(handler -> handler);

        server.register("*", () -> new AsyncServerExchangeHandler() {

            private final Random random = new Random(System.currentTimeMillis());
            private final AsyncEntityProducer entityProducer = AsyncEntityProducers.create(
                    "All is well");
            private final ReentrantLock lock = new ReentrantLock();

            @Override
            public void handleRequest(
                    final HttpRequest request,
                    final EntityDetails entityDetails,
                    final ResponseChannel responseChannel,
                    final HttpContext context) throws HttpException, IOException {

                executorResource.getExecutorService().execute(() -> {
                    try {
                        if (entityDetails != null) {
                            final Header h = request.getFirstHeader(HttpHeaders.EXPECT);
                            if (h != null && HeaderElements.CONTINUE.equalsIgnoreCase(h.getValue())) {
                                Thread.sleep(random.nextInt(1000));
                                responseChannel.sendInformation(new BasicHttpResponse(HttpStatus.SC_CONTINUE), context);
                            }
                            final HttpResponse response = new BasicHttpResponse(200);
                            lock.lock();
                            try {
                                responseChannel.sendResponse(response, entityProducer, context);
                            } finally {
                                lock.unlock();
                            }
                        }
                    } catch (final Exception ignore) {
                        // ignore
                    }
                });

            }

            @Override
            public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
                capacityChannel.update(Integer.MAX_VALUE);
            }

            @Override
            public void consume(final ByteBuffer src) throws IOException {
            }

            @Override
            public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
            }

            @Override
            public int available() {
                lock.lock();
                try {
                    return entityProducer.available();
                } finally {
                    lock.unlock();
                }
            }

            @Override
            public void produce(final DataStreamChannel channel) throws IOException {
                lock.lock();
                try {
                    entityProducer.produce(channel);
                } finally {
                    lock.unlock();
                }
            }

            @Override
            public void failed(final Exception cause) {
            }

            @Override
            public void releaseResources() {
            }

        });
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.configure(Http1Config.custom()
                .setWaitForContinueTimeout(Timeout.ofMilliseconds(100))
                .build());
        client.start();

        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final Queue<Future<Message<HttpResponse, String>>> queue = new LinkedList<>();
        for (int i = 0; i < 5; i++) {
            final BasicHttpRequest request = BasicRequestBuilder.post()
                    .setHttpHost(target)
                    .setPath("/")
                    .build();
            queue.add(streamEndpoint.execute(
                    new BasicRequestProducer(request, AsyncEntityProducers.create("Some important message")),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null));
        }
        while (!queue.isEmpty()) {
            final Future<Message<HttpResponse, String>> future = queue.remove();
            final Message<HttpResponse, String> result = future.get(LONG_TIMEOUT.getDuration(), LONG_TIMEOUT.getTimeUnit());
            Assertions.assertNotNull(result);
            final HttpResponse response = result.getHead();
            Assertions.assertNotNull(response);
            Assertions.assertEquals(200, response.getCode());
            Assertions.assertEquals("All is well", result.getBody());
        }
    }

    @Test
    void testMissingExpectContinueAckClientContinues() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

        // Disable 100-continue handshake on the server side
        server.configure(handler -> handler);

        server.register("/hello", () -> new SingleLineResponseHandler("Hi there back"));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.configure(Http1Config.custom()
                .setWaitForContinueTimeout(Timeout.ofMilliseconds(100))
                .build());
        client.start();

        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        for (int i = 0; i < 5; i++) {
            final BasicHttpRequest request = BasicRequestBuilder.post()
                    .setHttpHost(target)
                    .setPath("/hello")
                    .build();
            final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                    new BasicRequestProducer(request, AsyncEntityProducers.create("Hi there")),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
            final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assertions.assertNotNull(result);
            final HttpResponse response1 = result.getHead();
            final String entity1 = result.getBody();
            Assertions.assertNotNull(response1);
            Assertions.assertEquals(200, response1.getCode());
            Assertions.assertEquals("Hi there back", entity1);
        }
    }

    @Test
    void testPrematureResponse() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

        server.register("*", () -> new AsyncServerExchangeHandler() {

            private final AtomicReference<AsyncResponseProducer> responseProducer = new AtomicReference<>();

            @Override
            public void handleRequest(
                    final HttpRequest request,
                    final EntityDetails entityDetails,
                    final ResponseChannel responseChannel,
                    final HttpContext context) throws HttpException, IOException {
                final AsyncResponseProducer producer;
                final Header h = request.getFirstHeader("password");
                if (h != null && "secret".equals(h.getValue())) {
                    producer = new BasicResponseProducer(HttpStatus.SC_OK, "All is well");
                } else {
                    producer = new BasicResponseProducer(HttpStatus.SC_UNAUTHORIZED, "You shall not pass");
                }
                responseProducer.set(producer);
                producer.sendResponse(responseChannel, context);
            }

            @Override
            public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
                capacityChannel.update(Integer.MAX_VALUE);
            }

            @Override
            public void consume(final ByteBuffer src) throws IOException {
            }

            @Override
            public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
            }

            @Override
            public int available() {
                final AsyncResponseProducer producer = responseProducer.get();
                return producer.available();
            }

            @Override
            public void produce(final DataStreamChannel channel) throws IOException {
                final AsyncResponseProducer producer = responseProducer.get();
                producer.produce(channel);
            }

            @Override
            public void failed(final Exception cause) {
            }

            @Override
            public void releaseResources() {
            }
        });
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        for (int i = 0; i < 3; i++) {
            final BasicHttpRequest request1 = BasicRequestBuilder.post()
                    .setHttpHost(target)
                    .setPath("/echo")
                    .build();
            final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                    new BasicRequestProducer(request1, new MultiLineEntityProducer("0123456789abcdef", 100000)),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
            final Message<HttpResponse, String> result1 = future1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assertions.assertNotNull(result1);
            final HttpResponse response1 = result1.getHead();
            Assertions.assertNotNull(response1);
            Assertions.assertEquals(HttpStatus.SC_UNAUTHORIZED, response1.getCode());
            Assertions.assertEquals("You shall not pass", result1.getBody());

            Assertions.assertTrue(streamEndpoint.isOpen());
        }
        final BasicHttpRequest request1 = BasicRequestBuilder.post()
                .setHttpHost(target)
                .setPath("/echo")
                .build();
        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                new BasicRequestProducer(request1, new MultiBinEntityProducer(
                        new byte[] {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'},
                        100000,
                        ContentType.TEXT_PLAIN)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> result1 = future1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(result1);
        final HttpResponse response1 = result1.getHead();
        Assertions.assertNotNull(response1);
        Assertions.assertEquals(HttpStatus.SC_UNAUTHORIZED, response1.getCode());
        Assertions.assertEquals("You shall not pass", result1.getBody());
    }

    @Test
    void testSlowResponseConsumer() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

        server.register("/", () -> new MultiLineResponseHandler("0123456789abcd", 100));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.configure(Http1Config.custom()
                .setBufferSize(256)
                .build());
        client.start();

        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final BasicHttpRequest request1 = BasicRequestBuilder.get()
                .setHttpHost(target)
                .setPath("/")
                .build();
        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                new BasicRequestProducer(request1, null),
                new BasicResponseConsumer<>(new AbstractClassicEntityConsumer<String>(16, Executors.newSingleThreadExecutor()) {

                    @Override
                    protected String consumeData(
                            final ContentType contentType, final InputStream inputStream) throws IOException {
                        final Charset charset = ContentType.getCharset(contentType, StandardCharsets.US_ASCII);

                        final StringBuilder buffer = new StringBuilder();
                        try {
                            final byte[] tmp = new byte[16];
                            int l;
                            while ((l = inputStream.read(tmp)) != -1) {
                                buffer.append(charset.decode(ByteBuffer.wrap(tmp, 0, l)));
                                Thread.sleep(50);
                            }
                        } catch (final InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            throw new InterruptedIOException(ex.getMessage());
                        }
                        return buffer.toString();
                    }
                }),
                null);

        final Message<HttpResponse, String> result1 = future1.get(LONG_TIMEOUT.getDuration(), LONG_TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(result1);
        final HttpResponse response1 = result1.getHead();
        Assertions.assertNotNull(response1);
        Assertions.assertEquals(200, response1.getCode());
        final String s1 = result1.getBody();
        Assertions.assertNotNull(s1);
        final StringTokenizer t1 = new StringTokenizer(s1, "\r\n");
        while (t1.hasMoreTokens()) {
            Assertions.assertEquals("0123456789abcd", t1.nextToken());
        }
    }

    @Test
    void testSlowRequestProducer() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

        server.register("*", () -> new EchoHandler(2048));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final BasicHttpRequest request1 = BasicRequestBuilder.post()
                .setHttpHost(target)
                .setPath("/echo")
                .build();
        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                new BasicRequestProducer(request1, new AbstractClassicEntityProducer(4096, ContentType.TEXT_PLAIN, Executors.newSingleThreadExecutor()) {

                    @Override
                    protected void produceData(final ContentType contentType, final OutputStream outputStream) throws IOException {
                        final Charset charset = ContentType.getCharset(contentType, StandardCharsets.US_ASCII);
                        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, charset))) {
                            for (int i = 0; i < 500; i++) {
                                if (i % 100 == 0) {
                                    writer.flush();
                                    Thread.sleep(500);
                                }
                                writer.write("0123456789abcdef\r\n");
                            }
                        } catch (final InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            throw new InterruptedIOException(ex.getMessage());
                        }
                    }

                }),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> result1 = future1.get(LONG_TIMEOUT.getDuration(), LONG_TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(result1);
        final HttpResponse response1 = result1.getHead();
        Assertions.assertNotNull(response1);
        Assertions.assertEquals(200, response1.getCode());
        final String s1 = result1.getBody();
        Assertions.assertNotNull(s1);
        final StringTokenizer t1 = new StringTokenizer(s1, "\r\n");
        while (t1.hasMoreTokens()) {
            Assertions.assertEquals("0123456789abcdef", t1.nextToken());
        }
    }

    @Test
    void testSlowResponseProducer() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

        server.register("*", () -> new AbstractClassicServerExchangeHandler(2048, Executors.newSingleThreadExecutor()) {

            @Override
            protected void handle(
                    final HttpRequest request,
                    final InputStream requestStream,
                    final HttpResponse response,
                    final OutputStream responseStream,
                    final HttpContext context) throws IOException, HttpException {

                if (!"/hello".equals(request.getPath())) {
                    response.setCode(HttpStatus.SC_NOT_FOUND);
                    return;
                }
                if (!Method.POST.name().equalsIgnoreCase(request.getMethod())) {
                    response.setCode(HttpStatus.SC_NOT_IMPLEMENTED);
                    return;
                }
                if (requestStream == null) {
                    return;
                }
                final Header h1 = request.getFirstHeader(HttpHeaders.CONTENT_TYPE);
                final ContentType contentType = h1 != null ? ContentType.parse(h1.getValue()) : null;
                final Charset charset = ContentType.getCharset(contentType, StandardCharsets.US_ASCII);
                response.setCode(HttpStatus.SC_OK);
                response.setHeader(h1);
                try (final BufferedReader reader = new BufferedReader(new InputStreamReader(requestStream, charset));
                     final BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(responseStream, charset))) {
                    try {
                        String l;
                        int count = 0;
                        while ((l = reader.readLine()) != null) {
                            writer.write(l);
                            writer.write("\r\n");
                            count++;
                            if (count % 500 == 0) {
                                Thread.sleep(500);
                            }
                        }
                        writer.flush();
                    } catch (final InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new InterruptedIOException(ex.getMessage());
                    }
                }
            }
        });
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.configure(Http1Config.custom()
                .setBufferSize(256)
                .build());
        client.start();

        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final BasicHttpRequest request1 = BasicRequestBuilder.post()
                .setHttpHost(target)
                .setPath("/hello")
                .build();
        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                new BasicRequestProducer(request1, new MultiLineEntityProducer("0123456789abcd", 2000)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> result1 = future1.get(LONG_TIMEOUT.getDuration(), LONG_TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(result1);
        final HttpResponse response1 = result1.getHead();
        Assertions.assertNotNull(response1);
        Assertions.assertEquals(200, response1.getCode());
        final String s1 = result1.getBody();
        Assertions.assertNotNull(s1);
        final StringTokenizer t1 = new StringTokenizer(s1, "\r\n");
        while (t1.hasMoreTokens()) {
            Assertions.assertEquals("0123456789abcd", t1.nextToken());
        }
    }

    @Test
    void testPipelinedConnectionClose() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

        server.register("/hello*", () -> new SingleLineResponseHandler("Hi back"));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final BasicHttpRequest request1 = BasicRequestBuilder.post()
                .setHttpHost(target)
                .setPath("/hello-1")
                .build();
        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                new BasicRequestProducer(request1, AsyncEntityProducers.create("Hi there")),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);

        final BasicHttpRequest request2 = BasicRequestBuilder.post()
                .setHttpHost(target)
                .setPath("/hello-2")
                .addHeader(HttpHeaders.CONNECTION, "close")
                .build();
        final Future<Message<HttpResponse, String>> future2 = streamEndpoint.execute(
                new BasicRequestProducer(request2,
                        AsyncEntityProducers.create("Hi there")),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);

        final BasicHttpRequest request3 = BasicRequestBuilder.post()
                .setHttpHost(target)
                .setPath("/hello-3")
                .build();
        final Future<Message<HttpResponse, String>> future3 = streamEndpoint.execute(
                new BasicRequestProducer(request3, AsyncEntityProducers.create("Hi there")),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);

        final Message<HttpResponse, String> result1 = future1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(result1);
        final HttpResponse response1 = result1.getHead();
        final String entity1 = result1.getBody();
        Assertions.assertNotNull(response1);
        Assertions.assertEquals(200, response1.getCode());
        Assertions.assertEquals("Hi back", entity1);

        final Message<HttpResponse, String> result2 = future2.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(result2);
        final HttpResponse response2 = result2.getHead();
        final String entity2 = result2.getBody();
        Assertions.assertNotNull(response2);
        Assertions.assertEquals(200, response2.getCode());
        Assertions.assertEquals("Hi back", entity2);

        final Exception exception = Assertions.assertThrows(Exception.class, () ->
                future3.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()));
        assertThat(exception, CoreMatchers.anyOf(
                CoreMatchers.instanceOf(CancellationException.class),
                CoreMatchers.instanceOf(ExecutionException.class)));

        final BasicHttpRequest request4 = BasicRequestBuilder.post()
                .setHttpHost(target)
                .setPath("/hello-3")
                .build();
        final Future<Message<HttpResponse, String>> future4 = streamEndpoint.execute(
                new BasicRequestProducer(request4, AsyncEntityProducers.create("Hi there")),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Exception exception2 = Assertions.assertThrows(Exception.class, () ->
                future4.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()));
        assertThat(exception2, CoreMatchers.anyOf(
                CoreMatchers.instanceOf(CancellationException.class),
                CoreMatchers.instanceOf(ExecutionException.class)));
    }

    @Test
    void testPipelinedInvalidRequest() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

        server.register("/hello*", () -> new SingleLineResponseHandler("Hi back"));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final BasicHttpRequest request1 = BasicRequestBuilder.post()
                .setHttpHost(target)
                .setPath("/hello-1")
                .build();
        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                new BasicRequestProducer(request1, AsyncEntityProducers.create("Hi there")),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);

        final BasicHttpRequest request2 = BasicRequestBuilder.post()
                .setHttpHost(target)
                .setPath("/hello-2")
                .addHeader(HttpHeaders.HOST, "blah:blah")
                .build();
        final Future<Message<HttpResponse, String>> future2 = streamEndpoint.execute(
                new BasicRequestProducer(request2,
                        AsyncEntityProducers.create("Hi there")),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);

        final BasicHttpRequest request3 = BasicRequestBuilder.post()
                .setHttpHost(target)
                .setPath("/hello-3")
                .build();
        final Future<Message<HttpResponse, String>> future3 = streamEndpoint.execute(
                new BasicRequestProducer(request3, AsyncEntityProducers.create("Hi there")),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);

        final Message<HttpResponse, String> result1 = future1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(result1);
        final HttpResponse response1 = result1.getHead();
        final String entity1 = result1.getBody();
        Assertions.assertNotNull(response1);
        Assertions.assertEquals(200, response1.getCode());
        Assertions.assertEquals("Hi back", entity1);

        final Message<HttpResponse, String> result2 = future2.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(result2);
        final HttpResponse response2 = result2.getHead();
        final String entity2 = result2.getBody();
        Assertions.assertNotNull(response2);
        Assertions.assertEquals(400, response2.getCode());
        Assertions.assertFalse(entity2.isEmpty());


        final Exception exception = Assertions.assertThrows(Exception.class, () ->
                future3.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()));
        assertThat(exception, CoreMatchers.anyOf(
                CoreMatchers.instanceOf(CancellationException.class),
                CoreMatchers.instanceOf(ExecutionException.class)));
    }

    private static final byte[] GARBAGE = "garbage".getBytes(StandardCharsets.US_ASCII);

    private static class BrokenChunkEncoder extends AbstractContentEncoder {

        private final CharArrayBuffer lineBuffer;
        private boolean done;

        BrokenChunkEncoder(
                final WritableByteChannel channel,
                final SessionOutputBuffer buffer,
                final BasicHttpTransportMetrics metrics) {
            super(channel, buffer, metrics);
            lineBuffer = new CharArrayBuffer(16);
        }

        @Override
        public void complete(final List<? extends Header> trailers) throws IOException {
            super.complete(trailers);
        }

        @Override
        public int write(final ByteBuffer src) throws IOException {
            final int chunk;
            if (!done) {
                lineBuffer.clear();
                lineBuffer.append(Integer.toHexString(GARBAGE.length * 10));
                buffer().writeLine(lineBuffer);
                buffer().write(ByteBuffer.wrap(GARBAGE));
                done = true;
                chunk = GARBAGE.length;
            } else {
                chunk = 0;
            }
            final long bytesWritten = buffer().flush(channel());
            if (bytesWritten > 0) {
                metrics().incrementBytesTransferred(bytesWritten);
            }
            if (!buffer().hasData()) {
                channel().close();
            }
            return chunk;
        }

    }

    @Test
    void testTruncatedChunk() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

        final InetSocketAddress serverEndpoint = server.start(new InternalServerHttp1EventHandlerFactory(
                HttpProcessors.server(),
                (request, context) -> new MessageExchangeHandler<String>(new StringAsyncEntityConsumer()) {

                    @Override
                    protected void handle(
                            final Message<HttpRequest, String> request,
                            final AsyncServerRequestHandler.ResponseTrigger responseTrigger,
                            final HttpContext context) throws IOException, HttpException {
                        responseTrigger.submitResponse(
                                new BasicResponseProducer(new StringAsyncEntityProducer("useful stuff")), context);
                    }

                },
                Http1Config.DEFAULT,
                CharCodingConfig.DEFAULT,
                DefaultConnectionReuseStrategy.INSTANCE,
                null,
                null,
                scheme == URIScheme.HTTPS ? SSLTestContexts.createServerSSLContext() : null, null, null) {

            @Override
            protected ServerHttp1StreamDuplexer createServerHttp1StreamDuplexer(
                    final ProtocolIOSession ioSession,
                    final HttpProcessor httpProcessor,
                    final HandlerFactory<AsyncServerExchangeHandler> exchangeHandlerFactory,
                    final Http1Config http1Config,
                    final CharCodingConfig connectionConfig,
                    final ConnectionReuseStrategy connectionReuseStrategy,
                    final NHttpMessageParser<HttpRequest> incomingMessageParser,
                    final NHttpMessageWriter<HttpResponse> outgoingMessageWriter,
                    final ContentLengthStrategy incomingContentStrategy,
                    final ContentLengthStrategy outgoingContentStrategy,
                    final Http1StreamListener streamListener,
                    final Callback<Exception> exceptionCallback) {
                return new ServerHttp1StreamDuplexer(ioSession, httpProcessor, exchangeHandlerFactory,
                        scheme.id,
                        http1Config, connectionConfig, connectionReuseStrategy,
                        incomingMessageParser, outgoingMessageWriter,
                        incomingContentStrategy, outgoingContentStrategy,
                        streamListener,
                        exceptionCallback) {

                    @Override
                    protected ContentEncoder createContentEncoder(
                            final long len,
                            final WritableByteChannel channel,
                            final SessionOutputBuffer buffer,
                            final BasicHttpTransportMetrics metrics) throws HttpException {
                        if (len == ContentLengthStrategy.CHUNKED) {
                            return new BrokenChunkEncoder(channel, buffer, metrics);
                        } else {
                            return super.createContentEncoder(len, channel, buffer, metrics);
                        }
                    }

                };
            }

        });

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final BasicHttpRequest request = BasicRequestBuilder.get()
                .setHttpHost(target)
                .setPath("/hello")
                .build();
        final AsyncRequestProducer requestProducer = new BasicRequestProducer(request, null);
        final StringAsyncEntityConsumer entityConsumer = new StringAsyncEntityConsumer() {

            @Override
            public void releaseResources() {
                // Do not clear internal content buffer
            }

        };
        final BasicResponseConsumer<String> responseConsumer = new BasicResponseConsumer<>(entityConsumer);
        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(requestProducer, responseConsumer, null);
        final ExecutionException exception = Assertions.assertThrows(ExecutionException.class, () ->
                future1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()));
        final Throwable cause = exception.getCause();
        Assertions.assertTrue(cause instanceof MalformedChunkCodingException);
        Assertions.assertEquals("garbage", entityConsumer.generateContent());
    }

    @Test
    void testExceptionInHandler() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

        server.register("/hello", () -> new SingleLineResponseHandler("Hi there") {

            @Override
            protected void handle(
                    final Message<HttpRequest, String> request,
                    final AsyncServerRequestHandler.ResponseTrigger responseTrigger,
                    final HttpContext context) throws IOException, HttpException {
                throw new HttpException("Boom");
            }
        });
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final BasicHttpRequest request = BasicRequestBuilder.get()
                .setHttpHost(target)
                .setPath("/hello")
                .build();
        final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                new BasicRequestProducer(request, null),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(result);
        final HttpResponse response1 = result.getHead();
        final String entity1 = result.getBody();
        Assertions.assertNotNull(response1);
        Assertions.assertEquals(500, response1.getCode());
        Assertions.assertEquals("Boom", entity1);
    }

    @Test
    void testNoServiceHandler() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

        server.register("/ehh", () -> new SingleLineResponseHandler("Hi there"));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final BasicHttpRequest request = BasicRequestBuilder.get()
                .setHttpHost(target)
                .setPath("/hello")
                .build();
        final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                new BasicRequestProducer(request, null),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(result);
        final HttpResponse response1 = result.getHead();
        final String entity1 = result.getBody();
        Assertions.assertNotNull(response1);
        Assertions.assertEquals(404, response1.getCode());
        Assertions.assertEquals("Resource not found", entity1);
    }

    @Test
    void testResponseNoContent() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

        server.register("/hello", () -> new SingleLineResponseHandler("Hi there") {

            @Override
            protected void handle(
                    final Message<HttpRequest, String> request,
                    final AsyncServerRequestHandler.ResponseTrigger responseTrigger,
                    final HttpContext context) throws IOException, HttpException {
                final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_NO_CONTENT);
                responseTrigger.submitResponse(new BasicResponseProducer(response), context);
            }
        });
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final BasicHttpRequest request = BasicRequestBuilder.get()
                .setHttpHost(target)
                .setPath("/hello")
                .build();
        final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                new BasicRequestProducer(request, null),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(result);
        final HttpResponse response1 = result.getHead();
        Assertions.assertNotNull(response1);
        Assertions.assertEquals(204, response1.getCode());
        Assertions.assertNull(result.getBody());
    }

    @Test
    void testMessageWithTrailers() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

        server.register("/hello", () -> new AbstractServerExchangeHandler<Message<HttpRequest, String>>() {

            @Override
            protected AsyncRequestConsumer<Message<HttpRequest, String>> supplyConsumer(
                    final HttpRequest request,
                    final EntityDetails entityDetails,
                    final HttpContext context) throws HttpException {
                return new BasicRequestConsumer<>(entityDetails != null ? new StringAsyncEntityConsumer() : null);
            }

            @Override
            protected void handle(
                    final Message<HttpRequest, String> requestMessage,
                    final AsyncServerRequestHandler.ResponseTrigger responseTrigger,
                    final HttpContext context) throws HttpException, IOException {
                responseTrigger.submitResponse(new BasicResponseProducer(
                        HttpStatus.SC_OK,
                        new DigestingEntityProducer("MD5",
                                new StringAsyncEntityProducer("Hello back with some trailers"))), context);
            }
        });
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();

        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final BasicHttpRequest request1 = BasicRequestBuilder.get()
                .setHttpHost(target)
                .setPath("/hello")
                .build();
        final DigestingEntityConsumer<String> entityConsumer = new DigestingEntityConsumer<>("MD5", new StringAsyncEntityConsumer());
        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                new BasicRequestProducer(request1, null),
                new BasicResponseConsumer<>(entityConsumer), null);
        final Message<HttpResponse, String> result1 = future1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(result1);
        final HttpResponse response1 = result1.getHead();
        Assertions.assertNotNull(response1);
        Assertions.assertEquals(200, response1.getCode());
        Assertions.assertEquals("Hello back with some trailers", result1.getBody());

        final List<Header> trailers = entityConsumer.getTrailers();
        Assertions.assertNotNull(trailers);
        Assertions.assertEquals(2, trailers.size());
        final Map<String, String> map = new HashMap<>();
        for (final Header header: trailers) {
            map.put(TextUtils.toLowerCase(header.getName()), header.getValue());
        }
        final String digest = TextUtils.toHexString(entityConsumer.getDigest());
        Assertions.assertEquals("MD5", map.get("digest-algo"));
        Assertions.assertEquals(digest, map.get("digest"));
    }

    @Test
    void testProtocolException() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

        server.register("/boom", () -> new AsyncServerExchangeHandler() {

            private final StringAsyncEntityProducer entityProducer = new StringAsyncEntityProducer("Everyting is OK");

            @Override
            public void releaseResources() {
                entityProducer.releaseResources();
            }

            @Override
            public void handleRequest(
                    final HttpRequest request,
                    final EntityDetails entityDetails,
                    final ResponseChannel responseChannel,
                    final HttpContext context) throws HttpException, IOException {
                final String requestUri = request.getRequestUri();
                if (requestUri.endsWith("boom")) {
                    throw new ProtocolException("Boom!!!");
                }
                responseChannel.sendResponse(new BasicHttpResponse(200), entityProducer, context);
            }

            @Override
            public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
                capacityChannel.update(Integer.MAX_VALUE);
            }

            @Override
            public void consume(final ByteBuffer src) throws IOException {
            }

            @Override
            public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
                // empty
            }

            @Override
            public int available() {
                return entityProducer.available();
            }

            @Override
            public void produce(final DataStreamChannel channel) throws IOException {
                entityProducer.produce(channel);
            }

            @Override
            public void failed(final Exception cause) {
                releaseResources();
            }

        });

        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();
        final BasicHttpRequest request = BasicRequestBuilder.get()
                .setHttpHost(target)
                .setPath("/boom")
                .build();
        final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                new BasicRequestProducer(request, null),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(result);
        final HttpResponse response1 = result.getHead();
        final String entity1 = result.getBody();
        Assertions.assertNotNull(response1);
        Assertions.assertEquals(HttpStatus.SC_BAD_REQUEST, response1.getCode());
        Assertions.assertEquals("Boom!!!", entity1);
    }

    @Test
    void testHeaderTooLarge() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

        server.register("/hello", () -> new SingleLineResponseHandler("Hi there"));
        server.configure(Http1Config.custom()
                .setMaxLineLength(100)
                .build());
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();

        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final BasicHttpRequest request1 = BasicRequestBuilder.get()
                .setHttpHost(target)
                .setPath("/hello")
                .setHeader("big-f-header", "1234567890123456789012345678901234567890123456789012345678901234567890" +
                        "1234567890123456789012345678901234567890")
                .build();
        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                new BasicRequestProducer(request1, null),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> result1 = future1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(result1);
        final HttpResponse response1 = result1.getHead();
        Assertions.assertNotNull(response1);
        Assertions.assertEquals(431, response1.getCode());
        Assertions.assertEquals("Maximum line length limit exceeded", result1.getBody());
    }

    @Test
    void testHeaderTooLargePost() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

        server.register("/hello", () -> new SingleLineResponseHandler("Hi there"));
        server.configure(Http1Config.custom()
                .setMaxLineLength(100)
                .build());
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.configure(
                new DefaultHttpProcessor(RequestContent.INSTANCE, RequestTargetHost.INSTANCE, RequestConnControl.INSTANCE));
        client.start();

        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final BasicHttpRequest request1 = BasicRequestBuilder.post()
                .setHttpHost(target)
                .setPath("/hello")
                .setHeader("big-f-header", "1234567890123456789012345678901234567890123456789012345678901234567890" +
                        "1234567890123456789012345678901234567890")
                .build();

        final byte[] b = new byte[2048];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) ('a' + i % 10);
        }

        final Future<Message<HttpResponse, String>> future1 = streamEndpoint.execute(
                new BasicRequestProducer(request1, AsyncEntityProducers.create(b, ContentType.TEXT_PLAIN)),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> result1 = future1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(result1);
        final HttpResponse response1 = result1.getHead();
        Assertions.assertNotNull(response1);
        Assertions.assertEquals(431, response1.getCode());
        Assertions.assertEquals("Maximum line length limit exceeded", result1.getBody());
    }

    @Test
    void testInvalidRequestMessage() throws Exception {
        final Http1Config http1Config = Http1Config.DEFAULT;
        final Http1TestServer server = resources.server();
        server.configure(http1Config);
        server.configure(() -> new DefaultHttpRequestParser<HttpRequest>(http1Config, DefaultHttpRequestFactory.INSTANCE) {

            @Override
            protected HttpRequest createMessage(final CharArrayBuffer buffer) throws HttpException {
                throw new RuntimeException("Ka-boom");
            }

        });

        server.register("/hello", () -> new SingleLineResponseHandler("Hi there"));

        final InetSocketAddress serverEndpoint = server.start();
        final HttpHost target = target(serverEndpoint);

        final Http1TestClient client = resources.client();

        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(
                "localhost", serverEndpoint.getPort(), TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final BasicHttpRequest request = BasicRequestBuilder.get()
                .setHttpHost(target)
                .setPath("/hello")
                .build();

        final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
                new BasicRequestProducer(request, null),
                new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final ExecutionException executionException = Assertions.assertThrows(ExecutionException.class, () ->
                future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()));
        Assertions.assertInstanceOf(ConnectionClosedException.class, executionException.getCause());
    }

    @Test
    void testInvalidProtocolVersion() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

        server.register("/hello", () -> new SingleLineResponseHandler("Hi there"));
        final InetSocketAddress serverEndpoint = server.start();
        final HttpHost target = target(serverEndpoint);

        final LineFormatter lineFormatter = BasicLineFormatter.INSTANCE;
        client.configure(() -> new AbstractMessageWriter<HttpRequest>(lineFormatter) {

            @Override
            protected void writeHeadLine(final HttpRequest message, final CharArrayBuffer lineBuf) throws IOException {
                lineBuf.clear();
                lineFormatter.formatRequestLine(lineBuf, new RequestLine(
                    message.getMethod(),
                    message.getRequestUri(),
                    new HttpVersion(2, 1)));
            }

        });
        client.start();
        final Future<ClientSessionEndpoint> connectFuture = client.connect(
            "localhost", serverEndpoint.getPort(), TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final BasicHttpRequest request = BasicRequestBuilder.get()
                .setHttpHost(target)
                .setPath("/hello")
                .build();

        final Future<Message<HttpResponse, String>> future = streamEndpoint.execute(
            new BasicRequestProducer(request, null),
            new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null);
        final Message<HttpResponse, String> result = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertNotNull(result);
        final HttpResponse response = result.getHead();
        Assertions.assertNotNull(response);
        Assertions.assertEquals(505, response.getCode());
    }

    @Test
    void testDelayedRequestSubmission() throws Exception {
        final Http1TestServer server = resources.server();
        final Http1TestClient client = resources.client();

        server.register("/hello", () -> new SingleLineResponseHandler("All is well"));
        final InetSocketAddress serverEndpoint = server.start();

        final HttpHost target = target(serverEndpoint);

        client.start();

        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TIMEOUT);
        final ClientSessionEndpoint streamEndpoint = connectFuture.get();

        final Queue<Future<Message<HttpResponse, String>>> queue = new LinkedList<>();
        for (int i = 0; i < 5; i++) {
            final BasicHttpRequest request = BasicRequestBuilder.post()
                    .setHttpHost(target)
                    .setPath("/hello")
                    .build();
            final AsyncEntityProducer entityProducer = AsyncEntityProducers.create("Some important message");
            queue.add(streamEndpoint.execute(
                    new AsyncRequestProducer() {

                        private final Random random = new Random(System.currentTimeMillis());
                        private final ReentrantLock lock = new ReentrantLock();

                        @Override
                        public void sendRequest(final RequestChannel channel, final HttpContext context) throws HttpException, IOException {
                            executorResource.getExecutorService().execute(() -> {
                                try {
                                    Thread.sleep(random.nextInt(200));
                                    lock.lock();
                                    try {
                                        channel.sendRequest(request, entityProducer, context);
                                    } finally {
                                        lock.unlock();
                                    }
                                } catch (final Exception ignore) {
                                    // ignore
                                }
                            });
                        }

                        @Override
                        public boolean isRepeatable() {
                            lock.lock();
                            try {
                                return entityProducer.isRepeatable();
                            } finally {
                                lock.unlock();
                            }
                        }

                        @Override
                        public int available() {
                            lock.lock();
                            try {
                                return entityProducer.available();
                            } finally {
                                lock.unlock();
                            }
                        }

                        @Override
                        public void produce(final DataStreamChannel channel) throws IOException {
                            lock.lock();
                            try {
                                entityProducer.produce(channel);
                            } finally {
                                lock.unlock();
                            }
                        }

                        @Override
                        public void failed(final Exception cause) {
                            entityProducer.failed(cause);
                        }

                        @Override
                        public void releaseResources() {
                            entityProducer.releaseResources();
                        }

                    },
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()), null));
        }
        while (!queue.isEmpty()) {
            final Future<Message<HttpResponse, String>> future = queue.remove();
            final Message<HttpResponse, String> result = future.get(LONG_TIMEOUT.getDuration(), LONG_TIMEOUT.getTimeUnit());
            Assertions.assertNotNull(result);
            final HttpResponse response = result.getHead();
            Assertions.assertNotNull(response);
            Assertions.assertEquals(200, response.getCode());
            Assertions.assertEquals("All is well", result.getBody());
        }
    }

}

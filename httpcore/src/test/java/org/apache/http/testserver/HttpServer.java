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

package org.apache.http.testserver;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import org.apache.http.ConnectionClosedException;
import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpException;
import org.apache.http.HttpResponseFactory;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.HttpServerConnection;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpExpectationVerifier;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.HttpService;
import org.apache.http.protocol.ImmutableHttpProcessor;
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;
import org.apache.http.protocol.ResponseDate;
import org.apache.http.protocol.ResponseServer;
import org.apache.http.protocol.UriHttpRequestHandlerMapper;

public class HttpServer {

    private final HttpProcessor httpproc;
    private final ConnectionReuseStrategy connStrategy;
    private final HttpResponseFactory responseFactory;
    private final UriHttpRequestHandlerMapper reqistry;
    private final ServerSocket serversocket;

    private volatile HttpExpectationVerifier expectationVerifier;
    private volatile Thread listener;
    private volatile boolean shutdown;
    private volatile int timeout;

    public HttpServer() throws IOException {
        super();
        this.httpproc = new ImmutableHttpProcessor(
                new HttpResponseInterceptor[] {
                        new ResponseDate(),
                        new ResponseServer("TEST-SERVER/1.1"),
                        new ResponseContent(),
                        new ResponseConnControl()
                });
        this.connStrategy = DefaultConnectionReuseStrategy.INSTANCE;
        this.responseFactory = DefaultHttpResponseFactory.INSTANCE;
        this.reqistry = new UriHttpRequestHandlerMapper();
        this.serversocket = new ServerSocket(0);
    }

    public int getTimeout() {
        return this.timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public void registerHandler(
            final String pattern,
            final HttpRequestHandler handler) {
        this.reqistry.register(pattern, handler);
    }

    public void setExpectationVerifier(final HttpExpectationVerifier expectationVerifier) {
        this.expectationVerifier = expectationVerifier;
    }

    private HttpServerConnection acceptConnection() throws IOException {
        Socket socket = this.serversocket.accept();
        LoggingBHttpServerConnection conn = new LoggingBHttpServerConnection(8 * 1024);
        conn.bind(socket);
        conn.setSocketTimeout(this.timeout);
        return conn;
    }

    public int getPort() {
        return this.serversocket.getLocalPort();
    }

    public InetAddress getInetAddress() {
        return this.serversocket.getInetAddress();
    }

    public void start() {
        if (this.listener != null) {
            throw new IllegalStateException("Listener already running");
        }
        this.listener = new Thread(new Runnable() {

            public void run() {
                while (!shutdown && !Thread.interrupted()) {
                    try {
                        // Set up HTTP connection
                        HttpServerConnection conn = acceptConnection();
                        // Set up the HTTP service
                        HttpService httpService = new HttpService(
                                httpproc,
                                connStrategy,
                                responseFactory,
                                reqistry,
                                expectationVerifier);
                        // Start worker thread
                        Thread t = new WorkerThread(httpService, conn);
                        t.setDaemon(true);
                        t.start();
                    } catch (InterruptedIOException ex) {
                        break;
                    } catch (IOException e) {
                        break;
                    }
                }
            }

        });
        this.listener.start();
    }

    public void shutdown() {
        if (this.shutdown) {
            return;
        }
        this.shutdown = true;
        try {
            this.serversocket.close();
        } catch (IOException ignore) {}
        this.listener.interrupt();
        try {
            this.listener.join(1000);
        } catch (InterruptedException ignore) {}
    }

    static class WorkerThread extends Thread {

        private final HttpService httpservice;
        private final HttpServerConnection conn;

        public WorkerThread(
                final HttpService httpservice,
                final HttpServerConnection conn) {
            super();
            this.httpservice = httpservice;
            this.conn = conn;
        }

        @Override
        public void run() {
            HttpContext context = new BasicHttpContext(null);
            try {
                while (!Thread.interrupted() && this.conn.isOpen()) {
                    this.httpservice.handleRequest(this.conn, context);
                }
            } catch (ConnectionClosedException ex) {
            } catch (IOException ex) {
                System.err.println("I/O error: " + ex.getMessage());
            } catch (HttpException ex) {
                System.err.println("Unrecoverable HTTP protocol violation: " + ex.getMessage());
            } finally {
                try {
                    this.conn.shutdown();
                } catch (IOException ignore) {}
            }
        }

    }

}

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
package org.apache.http.benchmark;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.FileEntity;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.protocol.HTTP;

import java.security.KeyStore;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.net.SocketFactory;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.apache.http.HttpEntity;
import org.apache.http.entity.StringEntity;

/**
 * Main program of the HTTP benchmark.
 *
 *
 * @since 4.0
 */
public class HttpBenchmark {

    private final Config config;

    public static void main(String[] args) throws Exception {

        Options options = CommandLineUtils.getOptions();
        CommandLineParser parser = new PosixParser();
        CommandLine cmd = parser.parse(options, args);

        if (args.length == 0 || cmd.hasOption('h') || cmd.getArgs().length != 1) {
            CommandLineUtils.showUsage(options);
            System.exit(1);
        }

        Config config = new Config();
        CommandLineUtils.parseCommandLine(cmd, config);

        if (config.getUrl() == null) {
            CommandLineUtils.showUsage(options);
            System.exit(1);
        }

        HttpBenchmark httpBenchmark = new HttpBenchmark(config);
        httpBenchmark.execute();
    }

    public HttpBenchmark(final Config config) {
        super();
        this.config = config != null ? config : new Config();
    }

    private HttpRequest createRequest() {
        URL url = config.getUrl();
        HttpEntity entity = null;

        // Prepare requests for each thread
        if (config.getPayloadFile() != null) {
            FileEntity fe = new FileEntity(config.getPayloadFile());
            fe.setContentType(config.getContentType());
            fe.setChunked(config.isUseChunking());
            entity = fe;
        } else if (config.getPayloadText() != null) {
            StringEntity se = new StringEntity(config.getPayloadText(),
                    ContentType.parse(config.getContentType()));
            se.setChunked(config.isUseChunking());
            entity = se;
        }
        HttpRequest request;
        if ("POST".equals(config.getMethod())) {
            BasicHttpEntityEnclosingRequest httppost =
                    new BasicHttpEntityEnclosingRequest("POST", url.getPath());
            httppost.setEntity(entity);
            request = httppost;
        } else if ("PUT".equals(config.getMethod())) {
            BasicHttpEntityEnclosingRequest httpput =
                    new BasicHttpEntityEnclosingRequest("PUT", url.getPath());
            httpput.setEntity(entity);
            request = httpput;
        } else {
            String path = url.getPath();
            if (url.getQuery() != null && url.getQuery().length() > 0) {
                path += "?" + url.getQuery();
            } else if (path.trim().length() == 0) {
                path = "/";
            }
            request = new BasicHttpRequest(config.getMethod(), path);
        }

        if (!config.isKeepAlive()) {
            request.addHeader(new DefaultHeader(HTTP.CONN_DIRECTIVE, HTTP.CONN_CLOSE));
        }

        String[] headers = config.getHeaders();
        if (headers != null) {
            for (String s : headers) {
                int pos = s.indexOf(':');
                if (pos != -1) {
                    Header header = new DefaultHeader(s.substring(0, pos).trim(), s.substring(pos + 1));
                    request.addHeader(header);
                }
            }
        }

        if (config.isUseAcceptGZip()) {
            request.addHeader(new DefaultHeader("Accept-Encoding", "gzip"));
        }

        if (config.getSoapAction() != null && config.getSoapAction().length() > 0) {
            request.addHeader(new DefaultHeader("SOAPAction", config.getSoapAction()));
        }
        return request;
    }

    public String execute() throws Exception {
        Results results = doExecute();
        ResultProcessor.printResults(results);
        return "";
    }

    public Results doExecute() throws Exception {

        URL url = config.getUrl();
        HttpHost host = new HttpHost(url.getHost(), url.getPort(), url.getProtocol());

        ThreadPoolExecutor workerPool = new ThreadPoolExecutor(
                config.getThreads(), config.getThreads(), 5, TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>(),
            new ThreadFactory() {

                public Thread newThread(Runnable r) {
                    return new Thread(r, "ClientPool");
                }

            });
        workerPool.prestartAllCoreThreads();

        SocketFactory socketFactory = null;
        if ("https".equals(host.getSchemeName())) {
            TrustManager[] trustManagers = null;
            if (config.isDisableSSLVerification()) {
                // Create a trust manager that does not validate certificate chains
                trustManagers = new TrustManager[] {
                    new X509TrustManager() {

                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return null;
                        }

                        public void checkClientTrusted(
                            java.security.cert.X509Certificate[] certs, String authType) {
                        }

                        public void checkServerTrusted(
                            java.security.cert.X509Certificate[] certs, String authType) {
                        }
                    }
                };
            } else if (config.getTrustStorePath() != null) {
                KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
                FileInputStream instream = new FileInputStream(config.getTrustStorePath());
                try {
                    trustStore.load(instream, config.getTrustStorePath() != null ?
                            config.getTrustStorePath().toCharArray() : null);
                } finally {
                    try { instream.close(); } catch (IOException ignore) {}
                }
                TrustManagerFactory tmfactory = TrustManagerFactory.getInstance(
                        TrustManagerFactory.getDefaultAlgorithm());
                tmfactory.init(trustStore);
                trustManagers = tmfactory.getTrustManagers();
            }
            KeyManager[] keyManagers = null;
            if (config.getIdentityStorePath() != null) {
                KeyStore identityStore = KeyStore.getInstance(KeyStore.getDefaultType());
                FileInputStream instream = new FileInputStream(config.getIdentityStorePath());
                try {
                    identityStore.load(instream, config.getIdentityStorePassword() != null ?
                            config.getIdentityStorePassword().toCharArray() : null);
                } finally {
                    try { instream.close(); } catch (IOException ignore) {}
                }
                KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                    KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(identityStore, config.getIdentityStorePassword() != null ?
                        config.getIdentityStorePassword().toCharArray() : null);
                keyManagers = kmf.getKeyManagers();
            }
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(keyManagers, trustManagers, null);
            socketFactory = sc.getSocketFactory();
        }

        BenchmarkWorker[] workers = new BenchmarkWorker[config.getThreads()];
        for (int i = 0; i < workers.length; i++) {
            workers[i] = new BenchmarkWorker(
                    createRequest(),
                    host,
                    socketFactory,
                    config);
            workerPool.execute(workers[i]);
        }

        while (workerPool.getCompletedTaskCount() < config.getThreads()) {
            Thread.yield();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignore) {
            }
        }

        workerPool.shutdown();
        return ResultProcessor.collectResults(workers, host, config.getUrl().toString());
    }

}

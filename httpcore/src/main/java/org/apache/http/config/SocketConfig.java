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

package org.apache.http.config;

import java.net.SocketOptions;

import org.apache.http.annotation.Immutable;

/**
 * Socket configuration.
 *
 * @since 4.3
 */
@Immutable
public class SocketConfig implements Cloneable {

    public static final SocketConfig DEFAULT = new Builder().build();

    private final int soTimeout;
    private final boolean soReuseAddress;
    private final int soLinger;
    private final boolean soKeepAlive;
    private final boolean tcpNoDelay;

    SocketConfig(
            int soTimeout,
            boolean soReuseAddress,
            int soLinger,
            boolean soKeepAlive,
            boolean tcpNoDelay) {
        super();
        this.soTimeout = soTimeout;
        this.soReuseAddress = soReuseAddress;
        this.soLinger = soLinger;
        this.soKeepAlive = soKeepAlive;
        this.tcpNoDelay = tcpNoDelay;
    }

    /**
     * Determines the default socket timeout value for non-blocking I/O operations.
     * <p/>
     * Default: <code>0</code> (no timeout)
     *
     * @see SocketOptions#SO_TIMEOUT
     */
    public int getSoTimeout() {
        return soTimeout;
    }

    /**
     * Determines the default value of the {@link SocketOptions#SO_REUSEADDR} parameter
     * for newly created sockets.
     * <p/>
     * Default: <code>false</code>
     *
     * @see SocketOptions#SO_REUSEADDR
     */
    public boolean isSoReuseAddress() {
        return soReuseAddress;
    }

    /**
     * Determines the default value of the {@link SocketOptions#SO_LINGER} parameter
     * for newly created sockets.
     * <p/>
     * Default: <code>-1</code>
     *
     * @see SocketOptions#SO_LINGER
     */
    public int getSoLinger() {
        return soLinger;
    }

    /**
     * Determines the default value of the {@link SocketOptions#SO_KEEPALIVE} parameter
     * for newly created sockets.
     * <p/>
     * Default: <code>-1</code>
     *
     * @see SocketOptions#SO_KEEPALIVE
     */
    public boolean isSoKeepAlive() {
        return this.soKeepAlive;
    }

    /**
     * Determines the default value of the {@link SocketOptions#TCP_NODELAY} parameter
     * for newly created sockets.
     * <p/>
     * Default: <code>false</code>
     *
     * @see SocketOptions#TCP_NODELAY
     */
    public boolean isTcpNoDelay() {
        return tcpNoDelay;
    }

    @Override
    protected SocketConfig clone() throws CloneNotSupportedException {
        return (SocketConfig) super.clone();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("[soTimeout=").append(this.soTimeout)
                .append(", soReuseAddress=").append(this.soReuseAddress)
                .append(", soLinger=").append(this.soLinger)
                .append(", soKeepAlive=").append(this.soKeepAlive)
                .append(", tcpNoDelay=").append(this.tcpNoDelay)
                .append("]");
        return builder.toString();
    }

    public static SocketConfig.Builder custom() {
        return new Builder();
    }

    public static class Builder {

        private int soTimeout;
        private boolean soReuseAddress;
        private int soLinger;
        private boolean soKeepAlive;
        private boolean tcpNoDelay;

        Builder() {
            this.soLinger = -1;
            this.tcpNoDelay = true;
        }

        public Builder setSoTimeout(int soTimeout) {
            this.soTimeout = soTimeout;
            return this;
        }

        public Builder setSoReuseAddress(boolean soReuseAddress) {
            this.soReuseAddress = soReuseAddress;
            return this;
        }

        public Builder setSoLinger(int soLinger) {
            this.soLinger = soLinger;
            return this;
        }

        public Builder setSoKeepAlive(boolean soKeepAlive) {
            this.soKeepAlive = soKeepAlive;
            return this;
        }

        public Builder setTcpNoDelay(boolean tcpNoDelay) {
            this.tcpNoDelay = tcpNoDelay;
            return this;
        }

        public SocketConfig build() {
            return new SocketConfig(soTimeout, soReuseAddress, soLinger, soKeepAlive, tcpNoDelay);
        }

    }

}

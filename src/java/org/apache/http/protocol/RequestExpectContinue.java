/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
 * ====================================================================
 *
 *  Copyright 1999-2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.protocol;

import java.io.IOException;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpMutableRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpVersion;
import org.apache.http.params.HttpProtocolParams;

/**
 * <p>
 * </p>
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 * 
 * @since 4.0
 */
public class RequestExpectContinue implements HttpRequestInterceptor {

    private static final String EXPECT_DIRECTIVE = "Expect";
    
    private static final String CONTINUE = "100-Continue";
    
    public RequestExpectContinue() {
        super();
    }
    
    public void process(final HttpMutableRequest request, final HttpContext context) 
        throws HttpException, IOException {
        if (request == null) {
            throw new IllegalArgumentException("HTTP request may not be null");
        }
        if (request instanceof HttpEntityEnclosingRequest) {
            HttpEntity entity = ((HttpEntityEnclosingRequest)request).getEntity();
            // Do not send the expect header if request body is known to be empty
            if (entity != null && entity.getContentLength() != 0) { 
                HttpVersion ver = request.getRequestLine().getHttpVersion();
                if (HttpProtocolParams.useExpectContinue(request.getParams()) 
                        && ver.greaterEquals(HttpVersion.HTTP_1_1)) {
                    request.addHeader(new Header(EXPECT_DIRECTIVE, CONTINUE, true));
                }
            }
        }
    }
    
}
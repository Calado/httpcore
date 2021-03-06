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

package org.apache.http.impl.nio.reactor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.util.Arrays;
import java.util.Collection;

import org.apache.http.Consts;
import org.apache.http.nio.reactor.SessionInputBuffer;
import org.apache.http.nio.reactor.SessionOutputBuffer;
import org.apache.http.nio.util.ByteBufferAllocator;
import org.apache.http.nio.util.DirectByteBufferAllocator;
import org.apache.http.nio.util.HeapByteBufferAllocator;
import org.apache.http.util.CharArrayBuffer;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Simple tests for {@link SessionInputBuffer} and {@link SessionOutputBuffer}.
 */
@RunWith(Parameterized.class)
public class TestSessionInOutBuffers {

    private ByteBufferAllocator allocator;

    public TestSessionInOutBuffers(final ByteBufferAllocator allocator) {
        super();
        this.allocator = allocator;
    }

    @Parameters
    public static Collection<Object[]> getParameters() {

        return Arrays.asList(
                new Object[] { HeapByteBufferAllocator.INSTANCE },
                new Object[] { DirectByteBufferAllocator.INSTANCE });
    }

    private static WritableByteChannel newChannel(final ByteArrayOutputStream outstream) {
        return Channels.newChannel(outstream);
    }

    private static ReadableByteChannel newChannel(final byte[] bytes) {
        return Channels.newChannel(new ByteArrayInputStream(bytes));
    }

    private static ReadableByteChannel newChannel(final String s, final String charset)
            throws UnsupportedEncodingException {
        return Channels.newChannel(new ByteArrayInputStream(s.getBytes(charset)));
    }

    private static ReadableByteChannel newChannel(final String s)
            throws UnsupportedEncodingException {
        return newChannel(s, "US-ASCII");
    }

    @Test
    public void testReadLineChunks() throws Exception {
        SessionInputBuffer inbuf = new SessionInputBufferImpl(16, 16, null, this.allocator);

        ReadableByteChannel channel = newChannel("One\r\nTwo\r\nThree");

        inbuf.fill(channel);

        CharArrayBuffer line = new CharArrayBuffer(64);

        line.clear();
        Assert.assertTrue(inbuf.readLine(line, false));
        Assert.assertEquals("One", line.toString());

        line.clear();
        Assert.assertTrue(inbuf.readLine(line, false));
        Assert.assertEquals("Two", line.toString());

        line.clear();
        Assert.assertFalse(inbuf.readLine(line, false));

        channel = newChannel("\r\nFour");
        inbuf.fill(channel);

        line.clear();
        Assert.assertTrue(inbuf.readLine(line, false));
        Assert.assertEquals("Three", line.toString());

        inbuf.fill(channel);

        line.clear();
        Assert.assertTrue(inbuf.readLine(line, true));
        Assert.assertEquals("Four", line.toString());

        line.clear();
        Assert.assertFalse(inbuf.readLine(line, true));
    }

    @Test
    public void testWriteLineChunks() throws Exception {
        SessionOutputBuffer outbuf = new SessionOutputBufferImpl(16, 16, null, this.allocator);
        SessionInputBuffer inbuf = new SessionInputBufferImpl(16, 16, null, this.allocator);

        ReadableByteChannel inChannel = newChannel("One\r\nTwo\r\nThree");

        inbuf.fill(inChannel);

        CharArrayBuffer line = new CharArrayBuffer(64);

        line.clear();
        Assert.assertTrue(inbuf.readLine(line, false));
        Assert.assertEquals("One", line.toString());

        outbuf.writeLine(line);

        line.clear();
        Assert.assertTrue(inbuf.readLine(line, false));
        Assert.assertEquals("Two", line.toString());

        outbuf.writeLine(line);

        line.clear();
        Assert.assertFalse(inbuf.readLine(line, false));

        inChannel = newChannel("\r\nFour");
        inbuf.fill(inChannel);

        line.clear();
        Assert.assertTrue(inbuf.readLine(line, false));
        Assert.assertEquals("Three", line.toString());

        outbuf.writeLine(line);

        inbuf.fill(inChannel);

        line.clear();
        Assert.assertTrue(inbuf.readLine(line, true));
        Assert.assertEquals("Four", line.toString());

        outbuf.writeLine(line);

        line.clear();
        Assert.assertFalse(inbuf.readLine(line, true));

        ByteArrayOutputStream outstream = new ByteArrayOutputStream();
        WritableByteChannel outChannel = newChannel(outstream);
        outbuf.flush(outChannel);

        String s = new String(outstream.toByteArray(), "US-ASCII");
        Assert.assertEquals("One\r\nTwo\r\nThree\r\nFour\r\n", s);
    }

    @Test
    public void testBasicReadWriteLine() throws Exception {

        String[] teststrs = new String[5];
        teststrs[0] = "Hello";
        teststrs[1] = "This string should be much longer than the size of the line buffer " +
                "which is only 16 bytes for this test";
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < 15; i++) {
            buffer.append("123456789 ");
        }
        buffer.append("and stuff like that");
        teststrs[2] = buffer.toString();
        teststrs[3] = "";
        teststrs[4] = "And goodbye";

        SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 16, null, this.allocator);
        for (String teststr : teststrs) {
            outbuf.writeLine(teststr);
        }
        //this write operation should have no effect
        outbuf.writeLine((String)null);
        outbuf.writeLine((CharArrayBuffer)null);

        ByteArrayOutputStream outstream = new ByteArrayOutputStream();
        WritableByteChannel outChannel = newChannel(outstream);
        outbuf.flush(outChannel);

        ReadableByteChannel channel = newChannel(outstream.toByteArray());

        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 16, null, this.allocator);
        inbuf.fill(channel);

        for (String teststr : teststrs) {
            Assert.assertEquals(teststr, inbuf.readLine(true));
        }
        Assert.assertNull(inbuf.readLine(true));
        Assert.assertNull(inbuf.readLine(true));
    }

    @Test
    public void testComplexReadWriteLine() throws Exception {
        SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 16, null, this.allocator);
        outbuf.write(ByteBuffer.wrap(new byte[] {'a', '\n'}));
        outbuf.write(ByteBuffer.wrap(new byte[] {'\r', '\n'}));
        outbuf.write(ByteBuffer.wrap(new byte[] {'\r', '\r', '\n'}));
        outbuf.write(ByteBuffer.wrap(new byte[] {'\n'}));

        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < 14; i++) {
            buffer.append("a");
        }
        String s1 = buffer.toString();
        buffer.append("\r\n");
        outbuf.write(ByteBuffer.wrap(buffer.toString().getBytes("US-ASCII")));

        buffer.setLength(0);
        for (int i = 0; i < 15; i++) {
            buffer.append("a");
        }
        String s2 = buffer.toString();
        buffer.append("\r\n");
        outbuf.write(ByteBuffer.wrap(buffer.toString().getBytes("US-ASCII")));

        buffer.setLength(0);
        for (int i = 0; i < 16; i++) {
            buffer.append("a");
        }
        String s3 = buffer.toString();
        buffer.append("\r\n");
        outbuf.write(ByteBuffer.wrap(buffer.toString().getBytes("US-ASCII")));

        outbuf.write(ByteBuffer.wrap(new byte[] {'a'}));

        ByteArrayOutputStream outstream = new ByteArrayOutputStream();
        WritableByteChannel outChannel = newChannel(outstream);
        outbuf.flush(outChannel);

        ReadableByteChannel channel = newChannel(outstream.toByteArray());

        SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 16, null, this.allocator);
        inbuf.fill(channel);

        Assert.assertEquals("a", inbuf.readLine(true));
        Assert.assertEquals("", inbuf.readLine(true));
        Assert.assertEquals("\r", inbuf.readLine(true));
        Assert.assertEquals("", inbuf.readLine(true));
        Assert.assertEquals(s1, inbuf.readLine(true));
        Assert.assertEquals(s2, inbuf.readLine(true));
        Assert.assertEquals(s3, inbuf.readLine(true));
        Assert.assertEquals("a", inbuf.readLine(true));
        Assert.assertNull(inbuf.readLine(true));
        Assert.assertNull(inbuf.readLine(true));
    }

    @Test
    public void testReadOneByte() throws Exception {
        // make the buffer larger than that of transmitter
        byte[] out = new byte[40];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte)('0' + i);
        }
        ReadableByteChannel channel = newChannel(out);
        SessionInputBuffer inbuf = new SessionInputBufferImpl(16, 16, null, this.allocator);
        while (inbuf.fill(channel) > 0) {
        }

        byte[] in = new byte[40];
        for (int i = 0; i < in.length; i++) {
            in[i] = (byte)inbuf.read();
        }
        for (int i = 0; i < out.length; i++) {
            Assert.assertEquals(out[i], in[i]);
        }
    }

    @Test
    public void testReadByteBuffer() throws Exception {
        byte[] pattern = "0123456789ABCDEF".getBytes("US-ASCII");
        ReadableByteChannel channel = newChannel(pattern);
        SessionInputBuffer inbuf = new SessionInputBufferImpl(4096, 1024, null, this.allocator);
        while (inbuf.fill(channel) > 0) {
        }
        ByteBuffer dst = ByteBuffer.allocate(10);
        Assert.assertEquals(10, inbuf.read(dst));
        dst.flip();
        Assert.assertEquals(dst, ByteBuffer.wrap(pattern, 0, 10));
        dst.clear();
        Assert.assertEquals(6, inbuf.read(dst));
        dst.flip();
        Assert.assertEquals(dst, ByteBuffer.wrap(pattern, 10, 6));
    }

    @Test
    public void testReadByteBufferWithMaxLen() throws Exception {
        byte[] pattern = "0123456789ABCDEF".getBytes("US-ASCII");
        ReadableByteChannel channel = newChannel(pattern);
        SessionInputBuffer inbuf = new SessionInputBufferImpl(4096, 1024, null, this.allocator);
        while (inbuf.fill(channel) > 0) {
        }
        ByteBuffer dst = ByteBuffer.allocate(16);
        Assert.assertEquals(10, inbuf.read(dst, 10));
        dst.flip();
        Assert.assertEquals(dst, ByteBuffer.wrap(pattern, 0, 10));
        dst.clear();
        Assert.assertEquals(3, inbuf.read(dst, 3));
        dst.flip();
        Assert.assertEquals(dst, ByteBuffer.wrap(pattern, 10, 3));
        Assert.assertEquals(3, inbuf.read(dst, 20));
        dst.flip();
        Assert.assertEquals(dst, ByteBuffer.wrap(pattern, 13, 3));
    }

    @Test
    public void testReadToChannel() throws Exception {
        byte[] pattern = "0123456789ABCDEF".getBytes("US-ASCII");
        ReadableByteChannel channel = newChannel(pattern);
        SessionInputBuffer inbuf = new SessionInputBufferImpl(4096, 1024, null, this.allocator);
        while (inbuf.fill(channel) > 0) {
        }

        ByteArrayOutputStream outstream = new ByteArrayOutputStream();
        WritableByteChannel dst = newChannel(outstream);

        Assert.assertEquals(16, inbuf.read(dst));
        Assert.assertEquals(ByteBuffer.wrap(pattern), ByteBuffer.wrap(outstream.toByteArray()));
    }

    @Test
    public void testReadToChannelWithMaxLen() throws Exception {
        byte[] pattern = "0123456789ABCDEF".getBytes("US-ASCII");
        ReadableByteChannel channel = newChannel(pattern);
        SessionInputBuffer inbuf = new SessionInputBufferImpl(4096, 1024, null, this.allocator);
        while (inbuf.fill(channel) > 0) {
        }

        ByteArrayOutputStream outstream = new ByteArrayOutputStream();
        WritableByteChannel dst = newChannel(outstream);

        Assert.assertEquals(10, inbuf.read(dst, 10));
        Assert.assertEquals(3, inbuf.read(dst, 3));
        Assert.assertEquals(3, inbuf.read(dst, 10));
        Assert.assertEquals(ByteBuffer.wrap(pattern), ByteBuffer.wrap(outstream.toByteArray()));
    }

    @Test
    public void testWriteByteBuffer() throws Exception {
        byte[] pattern = "0123456789ABCDEF0123456789ABCDEF".getBytes("US-ASCII");

        SessionOutputBuffer outbuf = new SessionOutputBufferImpl(4096, 1024, null, this.allocator);
        ReadableByteChannel src = newChannel(pattern);
        outbuf.write(src);

        ByteArrayOutputStream outstream = new ByteArrayOutputStream();
        WritableByteChannel channel = newChannel(outstream);
        while (outbuf.flush(channel) > 0) {
        }
        Assert.assertEquals(ByteBuffer.wrap(pattern), ByteBuffer.wrap(outstream.toByteArray()));
    }

    @Test
    public void testWriteFromChannel() throws Exception {
        byte[] pattern = "0123456789ABCDEF0123456789ABCDEF".getBytes("US-ASCII");

        SessionOutputBuffer outbuf = new SessionOutputBufferImpl(4096, 1024, null, this.allocator);
        outbuf.write(ByteBuffer.wrap(pattern, 0, 16));
        outbuf.write(ByteBuffer.wrap(pattern, 16, 10));
        outbuf.write(ByteBuffer.wrap(pattern, 26, 6));

        ByteArrayOutputStream outstream = new ByteArrayOutputStream();
        WritableByteChannel channel = newChannel(outstream);
        while (outbuf.flush(channel) > 0) {
        }
        Assert.assertEquals(ByteBuffer.wrap(pattern), ByteBuffer.wrap(outstream.toByteArray()));
    }

    static final int SWISS_GERMAN_HELLO [] = {
        0x47, 0x72, 0xFC, 0x65, 0x7A, 0x69, 0x5F, 0x7A, 0xE4, 0x6D, 0xE4
    };

    static final int RUSSIAN_HELLO [] = {
        0x412, 0x441, 0x435, 0x43C, 0x5F, 0x43F, 0x440, 0x438,
        0x432, 0x435, 0x442
    };

    private static String constructString(int [] unicodeChars) {
        StringBuilder buffer = new StringBuilder();
        if (unicodeChars != null) {
            for (int unicodeChar : unicodeChars) {
                buffer.append((char)unicodeChar);
            }
        }
        return buffer.toString();
    }

    @Test
    public void testMultibyteCodedReadWriteLine() throws Exception {
        String s1 = constructString(SWISS_GERMAN_HELLO);
        String s2 = constructString(RUSSIAN_HELLO);
        String s3 = "Like hello and stuff";

        SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 16,
                Consts.UTF_8.newEncoder(), this.allocator);

        for (int i = 0; i < 10; i++) {
            outbuf.writeLine(s1);
            outbuf.writeLine(s2);
            outbuf.writeLine(s3);
        }

        ByteArrayOutputStream outstream = new ByteArrayOutputStream();
        WritableByteChannel outChannel = newChannel(outstream);
        outbuf.flush(outChannel);

        byte[] tmp = outstream.toByteArray();

        ReadableByteChannel channel = newChannel(tmp);
        SessionInputBuffer inbuf = new SessionInputBufferImpl(16, 16,
                Consts.UTF_8.newDecoder(), this.allocator);

        while (inbuf.fill(channel) > 0) {
        }

        for (int i = 0; i < 10; i++) {
            Assert.assertEquals(s1, inbuf.readLine(true));
            Assert.assertEquals(s2, inbuf.readLine(true));
            Assert.assertEquals(s3, inbuf.readLine(true));
        }
    }

    @Test
    public void testInputMatchesBufferLength() throws Exception {
        String s1 = "abcde";
        SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 5, null, this.allocator);
        outbuf.writeLine(s1);
    }

    @Test(expected=CharacterCodingException.class)
    public void testMalformedInputActionReport() throws Exception {
        String s = constructString(SWISS_GERMAN_HELLO);
        byte[] tmp = s.getBytes("ISO-8859-1");

        CharsetDecoder decoder = Consts.UTF_8.newDecoder();
        decoder.onMalformedInput(CodingErrorAction.REPORT);
        decoder.onUnmappableCharacter(CodingErrorAction.IGNORE);
        SessionInputBuffer inbuf = new SessionInputBufferImpl(16, 16, decoder, this.allocator);
        ReadableByteChannel channel = newChannel(tmp);
        while (inbuf.fill(channel) > 0) {
        }
        inbuf.readLine(true);
    }

    @Test
    public void testMalformedInputActionIgnore() throws Exception {
        String s = constructString(SWISS_GERMAN_HELLO);
        byte[] tmp = s.getBytes("ISO-8859-1");

        CharsetDecoder decoder = Consts.UTF_8.newDecoder();
        decoder.onMalformedInput(CodingErrorAction.IGNORE);
        decoder.onUnmappableCharacter(CodingErrorAction.IGNORE);
        SessionInputBuffer inbuf = new SessionInputBufferImpl(16, 16, decoder, this.allocator);
        ReadableByteChannel channel = newChannel(tmp);
        while (inbuf.fill(channel) > 0) {
        }
        String result = inbuf.readLine(true);
        Assert.assertEquals("Grezi_zm", result);
    }

    @Test
    public void testMalformedInputActionReplace() throws Exception {
        String s = constructString(SWISS_GERMAN_HELLO);
        byte[] tmp = s.getBytes("ISO-8859-1");

        CharsetDecoder decoder = Consts.UTF_8.newDecoder();
        decoder.onMalformedInput(CodingErrorAction.REPLACE);
        decoder.onUnmappableCharacter(CodingErrorAction.IGNORE);
        SessionInputBuffer inbuf = new SessionInputBufferImpl(16, 16, decoder, this.allocator);
        ReadableByteChannel channel = newChannel(tmp);
        while (inbuf.fill(channel) > 0) {
        }
        String result = inbuf.readLine(true);
        Assert.assertEquals("Gr\ufffdezi_z\ufffdm\ufffd", result);
    }

    @Test(expected=CharacterCodingException.class)
    public void testUnmappableInputActionReport() throws Exception {
        String s = "This text contains a circumflex \u0302!!!";
        CharsetEncoder encoder = Consts.ISO_8859_1.newEncoder();
        encoder.onMalformedInput(CodingErrorAction.IGNORE);
        encoder.onUnmappableCharacter(CodingErrorAction.REPORT);
        SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 16, encoder, this.allocator);
        outbuf.writeLine(s);
    }

    @Test
    public void testUnmappableInputActionIgnore() throws Exception {
        String s = "This text contains a circumflex \u0302!!!";
        CharsetEncoder encoder = Consts.ISO_8859_1.newEncoder();
        encoder.onMalformedInput(CodingErrorAction.IGNORE);
        encoder.onUnmappableCharacter(CodingErrorAction.IGNORE);
        SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 16, encoder, this.allocator);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        WritableByteChannel channel = newChannel(baos);
        outbuf.writeLine(s);
        outbuf.flush(channel);

        String result = new String(baos.toByteArray(), "US-ASCII");
        Assert.assertEquals("This text contains a circumflex !!!\r\n", result);
    }

    @Test
    public void testUnmappableInputActionReplace() throws Exception {
        String s = "This text contains a circumflex \u0302 !!!";
        CharsetEncoder encoder = Consts.ISO_8859_1.newEncoder();
        encoder.onMalformedInput(CodingErrorAction.IGNORE);
        encoder.onUnmappableCharacter(CodingErrorAction.REPLACE);
        SessionOutputBuffer outbuf = new SessionOutputBufferImpl(1024, 16, encoder, this.allocator);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        WritableByteChannel channel = newChannel(baos);
        outbuf.writeLine(s);
        outbuf.flush(channel);

        String result = new String(baos.toByteArray(), "US-ASCII");
        Assert.assertEquals("This text contains a circumflex ? !!!\r\n", result);
    }

}

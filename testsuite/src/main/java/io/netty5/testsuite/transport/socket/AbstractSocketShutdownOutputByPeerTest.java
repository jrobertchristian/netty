/*
 * Copyright 2019 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty5.testsuite.transport.socket;

import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.buffer.api.Buffer;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.ChannelShutdownDirection;
import io.netty5.channel.SimpleChannelInboundHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class AbstractSocketShutdownOutputByPeerTest<Socket> extends AbstractServerSocketTest {

    @Test
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testShutdownOutput(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testShutdownOutput);
    }

    public void testShutdownOutput(ServerBootstrap sb) throws Throwable {
        System.out.println("sb = " + sb);
        TestHandler h = new TestHandler();
        Socket s = newSocket();
        Channel sc = null;
        try {
            sc = sb.childHandler(h).childOption(ChannelOption.ALLOW_HALF_CLOSURE, true).bind().get();

            connect(s, sc.localAddress());
            write(s, 1);

            assertEquals(1, (int) h.queue.take());

            assertTrue(h.ch.isOpen());
            assertTrue(h.ch.isActive());
            assertFalse(h.ch.isShutdown(ChannelShutdownDirection.Inbound));
            assertFalse(h.ch.isShutdown(ChannelShutdownDirection.Outbound));

            shutdownOutput(s);

            h.halfClosure.await();

            assertTrue(h.ch.isOpen());
            assertTrue(h.ch.isActive());
            assertTrue(h.ch.isShutdown(ChannelShutdownDirection.Inbound));
            assertFalse(h.ch.isShutdown(ChannelShutdownDirection.Outbound));

            while (h.closure.getCount() != 1 && h.halfClosureCount.intValue() != 1) {
                Thread.sleep(100);
            }
        } finally {
            if (sc != null) {
                sc.close();
            }
            close(s);
        }
    }

    @Test
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testShutdownOutputWithoutOption(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testShutdownOutputWithoutOption);
    }

    public void testShutdownOutputWithoutOption(ServerBootstrap sb) throws Throwable {
        TestHandler h = new TestHandler();
        Socket s = newSocket();
        Channel sc = null;
        try {
            sc = sb.childHandler(h).bind().get();

            connect(s, sc.localAddress());
            write(s, 1);

            assertEquals(1, (int) h.queue.take());

            assertTrue(h.ch.isOpen());
            assertTrue(h.ch.isActive());
            assertFalse(h.ch.isShutdown(ChannelShutdownDirection.Inbound));
            assertFalse(h.ch.isShutdown(ChannelShutdownDirection.Outbound));

            shutdownOutput(s);

            h.closure.await();

            assertFalse(h.ch.isOpen());
            assertFalse(h.ch.isActive());
            assertTrue(h.ch.isShutdown(ChannelShutdownDirection.Inbound));
            assertTrue(h.ch.isShutdown(ChannelShutdownDirection.Outbound));

            while (h.halfClosure.getCount() != 1 && h.halfClosureCount.intValue() != 0) {
                Thread.sleep(100);
            }
        } finally {
            if (sc != null) {
                sc.close();
            }
            close(s);
        }
    }

    protected abstract void shutdownOutput(Socket s) throws IOException;

    protected abstract void connect(Socket s, SocketAddress address) throws IOException;

    protected abstract void close(Socket s) throws IOException;

    protected abstract void write(Socket s, int data) throws IOException;

    protected abstract Socket newSocket();

    private static class TestHandler extends SimpleChannelInboundHandler<Buffer> {
        volatile Channel ch;
        final BlockingQueue<Byte> queue = new LinkedBlockingQueue<>();
        final CountDownLatch halfClosure = new CountDownLatch(1);
        final CountDownLatch closure = new CountDownLatch(1);
        final AtomicInteger halfClosureCount = new AtomicInteger();

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            ch = ctx.channel();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            closure.countDown();
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, Buffer msg) throws Exception {
            queue.offer(msg.readByte());
        }

        @Override
        public void channelShutdown(ChannelHandlerContext ctx, ChannelShutdownDirection direction) throws Exception {
            if (direction == ChannelShutdownDirection.Inbound) {
                halfClosureCount.incrementAndGet();
                halfClosure.countDown();
            }
        }
    }
}

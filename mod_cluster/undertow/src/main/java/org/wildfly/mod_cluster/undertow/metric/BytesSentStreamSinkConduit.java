/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.mod_cluster.undertow.metric;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.atomic.LongAdder;

import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.AbstractSinkConduit;
import org.xnio.conduits.StreamSinkConduit;

/**
 * Implementation of {@link StreamSinkConduit} wrapping that wraps around byte-transferring methods to calculate total
 * number of bytes transferred leveraging {@link LongAdder}.
 *
 * @author Radoslav Husar
 * @since 8.0
 */
public class BytesSentStreamSinkConduit extends AbstractSinkConduit implements StreamSinkConduit {

    private final StreamSinkConduit next;
    private static final LongAdder bytesSent = new LongAdder();

    public BytesSentStreamSinkConduit(StreamSinkConduit next) {
        super(next);
        this.next = next;
    }

    @Override
    public long transferFrom(FileChannel src, long position, long count) throws IOException {
        long bytes = next.transferFrom(src, position, count);
        bytesSent.add(bytes);
        return bytes;
    }


    @Override
    public long transferFrom(StreamSourceChannel source, long count, ByteBuffer throughBuffer) throws IOException {
        long bytes = next.transferFrom(source, count, throughBuffer);
        bytesSent.add(bytes);
        return bytes;
    }


    @Override
    public int write(ByteBuffer src) throws IOException {
        int bytes = next.write(src);
        bytesSent.add(bytes);
        return bytes;
    }


    @Override
    public long write(ByteBuffer[] srcs, int offs, int len) throws IOException {
        long bytes = next.write(srcs, offs, len);
        bytesSent.add(bytes);
        return bytes;
    }

    @Override
    public int writeFinal(ByteBuffer src) throws IOException {
        int bytes = next.writeFinal(src);
        bytesSent.add(bytes);
        return bytes;
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs, int offset, int length) throws IOException {
        long bytes = next.writeFinal(srcs, offset, length);
        bytesSent.add(bytes);
        return bytes;
    }

    public static long getBytesSent() {
        return bytesSent.longValue();
    }
}

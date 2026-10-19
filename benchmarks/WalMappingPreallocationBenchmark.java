import com.onyx.diskmap.store.impl.WholeFileMapping;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Locale;

/** Isolated real-file WAL mapping benchmark. Never opens an application database. */
public final class WalMappingPreallocationBenchmark {
    private static final long MIB = 1024L * 1024L;
    private static final long ROTATION_BYTES = 20L * MIB;
    private static final long RESERVED_BYTES = 24L * MIB;
    private static final long TOTAL_BYTES = 22L * MIB;
    private static final long FORCE_INTERVAL_NANOS = 20_000_000L;

    public static void main(String[] args) throws Exception {
        int repetitions = args.length == 0 ? 3 : Integer.parseInt(args[0]);
        if (repetitions < 1) throw new IllegalArgumentException("repetitions must be positive");
        System.out.println("phase,iteration,payload_bytes,initial_mib,append_ms,open_ms,close_ms,total_ms,p99_append_us,max_append_us,maps,metadata_forces,periodic_forces,verified_bytes");
        for (int payloadBytes : new int[]{4096, 16384}) {
            for (int iteration = -1; iteration < repetitions; iteration++) {
                // Alternate order, including a complete untimed pair for each frame size.
                long[] initialCapacities = (iteration & 1) == 0
                        ? new long[]{0L, RESERVED_BYTES} : new long[]{RESERVED_BYTES, 0L};
                for (long initialCapacity : initialCapacities) {
                    measure(payloadBytes, initialCapacity, iteration);
                }
            }
        }
    }

    private static void measure(int payloadBytes, long initialCapacity, int iteration) throws Exception {
        Path directory = Files.createTempDirectory("onyx-wal-preallocation-benchmark-");
        int frameBytes = payloadBytes + 5;
        byte[] frame = new byte[frameBytes];
        for (int index = 5; index < frame.length; index++) frame[index] = (byte) (index * 31 + 7);
        ByteBuffer.wrap(frame).put((byte) 1).putInt(frameBytes - 5);
        ByteBuffer header = ByteBuffer.wrap(frame, 0, 5).slice();
        ByteBuffer payload = ByteBuffer.wrap(frame, 5, frameBytes - 5).slice();
        long[] appendNanos = new long[(int) ((TOTAL_BYTES + frameBytes - 1L) / frameBytes)];
        int sample = 0;
        long appendTime = 0L;
        long openTime = 0L;
        long closeTime = 0L;
        long maps = 0L;
        long metadataForces = 0L;
        long periodicForces = 0L;
        long verifiedBytes = 0L;
        int fileNumber = 0;
        try {
            for (long totalPosition = 0L; totalPosition < TOTAL_BYTES;) {
                Path file = directory.resolve(fileNumber++ + ".wal");
                long start = System.nanoTime();
                TrackingChannel channel = new TrackingChannel(FileChannel.open(file,
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.READ, StandardOpenOption.WRITE));
                WholeFileMapping mapping = null;
                long position = 0L;
                try {
                    mapping = new WholeFileMapping(channel, 4L * MIB, initialCapacity, true);
                    openTime += System.nanoTime() - start;
                    long forceDeadline = System.nanoTime() + FORCE_INTERVAL_NANOS;
                    while (position < ROTATION_BYTES && totalPosition < TOTAL_BYTES) {
                        header.clear();
                        payload.clear();
                        start = System.nanoTime();
                        position += mapping.write(header, position);
                        position += mapping.write(payload, position);
                        long now = System.nanoTime();
                        if (now >= forceDeadline) {
                            mapping.force();
                            periodicForces++;
                            forceDeadline = System.nanoTime() + FORCE_INTERVAL_NANOS;
                        }
                        long duration = System.nanoTime() - start;
                        appendNanos[sample++] = duration;
                        appendTime += duration;
                        totalPosition += frameBytes;
                    }
                    start = System.nanoTime();
                    mapping.close();
                    mapping = null;
                    channel.truncate(position);
                    channel.force(true);
                    channel.close();
                    closeTime += System.nanoTime() - start;
                    maps += channel.maps;
                    metadataForces += channel.metadataForces;
                } finally {
                    if (mapping != null) mapping.close();
                    channel.close();
                }
                verify(file, frame, position);
                verifiedBytes += position;
                Files.delete(file);
            }
            Arrays.sort(appendNanos);
            System.out.printf(Locale.ROOT,
                    "%s,%d,%d,%d,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%d,%d,%d,%d%n",
                    iteration < 0 ? "warmup" : "measured", iteration, payloadBytes,
                    initialCapacity == 0L ? 4L : initialCapacity / MIB,
                    appendTime / 1e6, openTime / 1e6, closeTime / 1e6,
                    (appendTime + openTime + closeTime) / 1e6,
                    appendNanos[(int) Math.ceil(sample * 0.99) - 1] / 1e3,
                    appendNanos[sample - 1] / 1e3,
                    maps, metadataForces, periodicForces, verifiedBytes);
        } finally {
            try (var files = Files.list(directory)) {
                for (Path file : files.toList()) Files.deleteIfExists(file);
            }
            Files.delete(directory);
        }
    }

    private static void verify(Path file, byte[] frame, long expectedSize) throws IOException {
        if (Files.size(file) != expectedSize) throw new AssertionError("Incorrect WAL logical length");
        ByteBuffer bytes = ByteBuffer.allocate(64 * 1024);
        long offset = 0L;
        try (FileChannel input = FileChannel.open(file, StandardOpenOption.READ)) {
            while (input.read(bytes) >= 0) {
                bytes.flip();
                while (bytes.hasRemaining()) {
                    if (bytes.get() != frame[(int) (offset++ % frame.length)]) {
                        throw new AssertionError("Incorrect WAL byte at " + (offset - 1));
                    }
                }
                bytes.clear();
            }
        }
        if (offset != expectedSize) throw new AssertionError("Incomplete WAL read");
    }

    private static final class TrackingChannel extends FileChannel {
        private final FileChannel delegate;
        long maps;
        long metadataForces;

        TrackingChannel(FileChannel delegate) { this.delegate = delegate; }
        @Override public MemorySegment map(MapMode mode, long position, long size, Arena arena) throws IOException {
            maps++;
            return delegate.map(mode, position, size, arena);
        }
        @Override public void force(boolean metadata) throws IOException {
            if (metadata) metadataForces++;
            delegate.force(metadata);
        }
        @Override public int read(ByteBuffer dst) throws IOException { return delegate.read(dst); }
        @Override public long read(ByteBuffer[] dsts, int offset, int length) throws IOException { return delegate.read(dsts, offset, length); }
        @Override public int read(ByteBuffer dst, long position) throws IOException { return delegate.read(dst, position); }
        @Override public int write(ByteBuffer src) throws IOException { return delegate.write(src); }
        @Override public long write(ByteBuffer[] srcs, int offset, int length) throws IOException { return delegate.write(srcs, offset, length); }
        @Override public int write(ByteBuffer src, long position) throws IOException { return delegate.write(src, position); }
        @Override public long position() throws IOException { return delegate.position(); }
        @Override public FileChannel position(long position) throws IOException { delegate.position(position); return this; }
        @Override public long size() throws IOException { return delegate.size(); }
        @Override public FileChannel truncate(long size) throws IOException { delegate.truncate(size); return this; }
        @Override public long transferTo(long position, long count, WritableByteChannel target) throws IOException { return delegate.transferTo(position, count, target); }
        @Override public long transferFrom(ReadableByteChannel src, long position, long count) throws IOException { return delegate.transferFrom(src, position, count); }
        @Override public MappedByteBuffer map(MapMode mode, long position, long size) throws IOException { return delegate.map(mode, position, size); }
        @Override public FileLock lock(long position, long size, boolean shared) throws IOException { return delegate.lock(position, size, shared); }
        @Override public FileLock tryLock(long position, long size, boolean shared) throws IOException { return delegate.tryLock(position, size, shared); }
        @Override protected void implCloseChannel() throws IOException { delegate.close(); }
    }
}

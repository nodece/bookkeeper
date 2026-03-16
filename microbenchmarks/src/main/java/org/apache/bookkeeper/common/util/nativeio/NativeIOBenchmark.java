/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.bookkeeper.common.util.nativeio;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Linux benchmark for comparing {@code posix_fadvise} hints while scanning a file with NativeIO.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 1, time = 3)
@Measurement(iterations = 2, time = 3)
@State(Scope.Thread)
public class NativeIOBenchmark {

    private static final int POSIX_FADV_SEQUENTIAL = 2;
    private static final int POSIX_FADV_RANDOM = 1;
    private static final int POSIX_FADV_WILLNEED = 3;
    private static final int POSIX_FADV_DONTNEED = 4;
    private static final int ALIGNMENT = 4096;
    private static final int WRITE_CHUNK_SIZE = 1024 * 1024;

    @Param({"NONE", "SEQUENTIAL", "RANDOM", "WILLNEED"})
    public String advice;

    @Param({"64"})
    public int fileSizeMb;

    @Param({"131072"})
    public int readSize;

    private AdviceMode adviceMode;
    private NativeIO nativeIO;
    private Path tempFile;
    private int fd = -1;
    private long alignedBuffer;
    private long fileSizeBytes;

    @Setup(Level.Trial)
    public void setUp() throws Exception {
        String osName = System.getProperty("os.name", "");
        if (!osName.toLowerCase().contains("linux")) {
            throw new IllegalStateException("NativeIOBenchmark requires Linux, found: " + osName);
        }

        adviceMode = AdviceMode.valueOf(advice);
        fileSizeBytes = fileSizeMb * 1024L * 1024L;
        if (fileSizeBytes < readSize) {
            throw new IllegalArgumentException("fileSizeMb must be at least one readSize chunk");
        }

        tempFile = createBenchmarkFile(fileSizeBytes);
        nativeIO = new NativeIOImpl();
        fd = nativeIO.open(tempFile.toString(), NativeIO.O_RDONLY, 0);
        alignedBuffer = nativeIO.posix_memalign(ALIGNMENT, readSize);
    }

    @TearDown(Level.Invocation)
    public void clearFileCacheHint() throws Exception {
        if (fd >= 0) {
            nativeIO.posix_fadvise(fd, 0, fileSizeBytes, POSIX_FADV_DONTNEED);
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() throws Exception {
        Exception failure = null;
        if (nativeIO != null && alignedBuffer != 0) {
            try {
                nativeIO.free(alignedBuffer);
            } catch (Exception e) {
                failure = e;
            }
        }
        if (nativeIO != null && fd >= 0) {
            try {
                nativeIO.close(fd);
            } catch (Exception e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (tempFile != null) {
            Files.deleteIfExists(tempFile);
        }
        if (failure != null) {
            throw failure;
        }
    }

    @Benchmark
    public long readWholeFile() throws Exception {
        if (adviceMode.flag != null) {
            nativeIO.posix_fadvise(fd, 0, fileSizeBytes, adviceMode.flag);
        }

        long totalBytesRead = 0;
        while (totalBytesRead < fileSizeBytes) {
            int bytesToRead = (int) Math.min(readSize, fileSizeBytes - totalBytesRead);
            long bytesRead = nativeIO.pread(fd, alignedBuffer, bytesToRead, totalBytesRead);
            if (bytesRead <= 0) {
                throw new IOException("Unexpected EOF while reading benchmark file at offset " + totalBytesRead);
            }
            totalBytesRead += bytesRead;
        }
        return totalBytesRead;
    }

    private static Path createBenchmarkFile(long fileSizeBytes) throws IOException {
        Path path = Files.createTempFile("native-io-benchmark", ".bin");
        byte[] chunk = new byte[WRITE_CHUNK_SIZE];
        Random random = new Random(0x5A17L);
        try (OutputStream output = Files.newOutputStream(path, StandardOpenOption.TRUNCATE_EXISTING)) {
            long remaining = fileSizeBytes;
            while (remaining > 0) {
                random.nextBytes(chunk);
                int bytesToWrite = (int) Math.min(chunk.length, remaining);
                output.write(chunk, 0, bytesToWrite);
                remaining -= bytesToWrite;
            }
        }
        return path;
    }

    private enum AdviceMode {
        NONE(null),
        SEQUENTIAL(POSIX_FADV_SEQUENTIAL),
        RANDOM(POSIX_FADV_RANDOM),
        WILLNEED(POSIX_FADV_WILLNEED);

        private final Integer flag;

        AdviceMode(Integer flag) {
            this.flag = flag;
        }
    }
}

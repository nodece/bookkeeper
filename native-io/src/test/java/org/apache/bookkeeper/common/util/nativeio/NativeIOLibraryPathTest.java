/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.bookkeeper.common.util.nativeio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;

import org.junit.Test;

public class NativeIOLibraryPathTest {

    @Test
    public void testLinuxLibraryPath() {
        assertEquals("/lib/libnative-io.so", NativeIOLibraryPath.libraryPath("Linux", "amd64"));
        assertEquals("/lib/libnative-io.so", NativeIOLibraryPath.libraryPath("Linux", "aarch64"));
    }

    @Test
    public void testMacLibraryPath() {
        assertEquals("/lib/libnative-io.jnilib", NativeIOLibraryPath.libraryPath("Mac OS X", "x86_64"));
    }

    @Test
    public void testExplicitLibraryPathPriority() {
        assertEquals("/tmp/libnative-io.so",
                NativeIOLibraryPath.configuredLibraryPath("/tmp/libnative-io.so", "/tmp/ignored.so"));
        assertEquals("/tmp/from-env.so",
                NativeIOLibraryPath.configuredLibraryPath(null, "/tmp/from-env.so"));
        assertNull(NativeIOLibraryPath.configuredLibraryPath("  ", ""));
    }

    @Test
    public void testBackendParsing() {
        assertEquals(NativeIOLibraryPath.Backend.AUTO, NativeIOLibraryPath.parseBackend(null, null));
        assertEquals(NativeIOLibraryPath.Backend.C, NativeIOLibraryPath.parseBackend("c", null));
        assertEquals(NativeIOLibraryPath.Backend.RUST, NativeIOLibraryPath.parseBackend(null, "rust"));
    }

    @Test
    public void testLinuxAutoCandidates() {
        assertEquals(
                Arrays.asList("/lib/libnative-io.so", "/lib/rust/libnative-io.so"),
                NativeIOLibraryPath.libraryCandidates(NativeIOLibraryPath.Backend.AUTO, "Linux", "amd64"));
    }

    @Test
    public void testLinuxRustCandidates() {
        assertEquals(
                Arrays.asList("/lib/rust/libnative-io.so"),
                NativeIOLibraryPath.libraryCandidates(NativeIOLibraryPath.Backend.RUST, "Linux", "amd64"));
    }

    @Test(expected = IllegalStateException.class)
    public void testUnsupportedBackend() {
        NativeIOLibraryPath.parseBackend("wat", null);
    }
}

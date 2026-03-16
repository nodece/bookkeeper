/*
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
 *
 */
package org.apache.bookkeeper.common.util.nativeio;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class NativeIOLibraryPath {

    private static final String MAC_LIBRARY_PATH = "/lib/libnative-io.jnilib";
    private static final String LEGACY_LINUX_LIBRARY_PATH = "/lib/libnative-io.so";
    private static final String RUST_LINUX_LIBRARY_PATH = "/lib/rust/libnative-io.so";
    private static final String RUST_MAC_LIBRARY_PATH = "/lib/rust/libnative-io.jnilib";
    static final String LIBRARY_PATH_ENV = "BOOKKEEPER_NATIVE_IO_LIBRARY_PATH";
    static final String LIBRARY_PATH_PROPERTY = "bookkeeper.native.io.library.path";
    static final String BACKEND_ENV = "BOOKKEEPER_NATIVE_IO_IMPL";
    static final String BACKEND_PROPERTY = "bookkeeper.native.io.impl";

    private NativeIOLibraryPath() {
    }

    enum Backend {
        AUTO,
        C,
        RUST
    }

    static String configuredLibraryPath() {
        return configuredLibraryPath(System.getProperty(LIBRARY_PATH_PROPERTY), System.getenv(LIBRARY_PATH_ENV));
    }

    static String configuredLibraryPath(String propertyValue, String envValue) {
        String configuredProperty = trimToNull(propertyValue);
        if (configuredProperty != null) {
            return configuredProperty;
        }
        return trimToNull(envValue);
    }

    static Backend configuredBackend() {
        return parseBackend(System.getProperty(BACKEND_PROPERTY), System.getenv(BACKEND_ENV));
    }

    static Backend parseBackend(String propertyValue, String envValue) {
        String configuredValue = trimToNull(propertyValue);
        if (configuredValue == null) {
            configuredValue = trimToNull(envValue);
        }
        if (configuredValue == null) {
            return Backend.AUTO;
        }
        switch (configuredValue.toLowerCase(Locale.US)) {
            case "auto":
                return Backend.AUTO;
            case "c":
                return Backend.C;
            case "rust":
                return Backend.RUST;
            default:
                throw new IllegalStateException("Unsupported native-io backend: " + configuredValue);
        }
    }

    static List<String> currentPlatformLibraryCandidates() {
        return libraryCandidates(configuredBackend(), System.getProperty("os.name"), System.getProperty("os.arch"));
    }

    static List<String> libraryCandidates(Backend backend, String osName, String osArch) {
        List<String> paths = new ArrayList<>();
        String normalizedOsName = osName.toLowerCase(Locale.US);
        if (normalizedOsName.contains("mac")) {
            addMacLibraryCandidates(paths, backend);
            return paths;
        }
        if (normalizedOsName.contains("linux")) {
            addLinuxLibraryCandidates(paths, backend, osArch);
            return paths;
        }
        throw new IllegalStateException("OS not supported by Native-IO utils: " + osName);
    }

    static String libraryPath(String osName, String osArch) {
        return cLibraryPath(osName);
    }

    private static String cLibraryPath(String osName) {
        String normalizedOsName = osName.toLowerCase(Locale.US);
        if (normalizedOsName.contains("mac")) {
            return MAC_LIBRARY_PATH;
        }
        if (normalizedOsName.contains("linux")) {
            return LEGACY_LINUX_LIBRARY_PATH;
        }
        throw new IllegalStateException("OS not supported by Native-IO utils: " + osName);
    }

    private static void addMacLibraryCandidates(List<String> paths, Backend backend) {
        switch (backend) {
            case AUTO:
                paths.add(MAC_LIBRARY_PATH);
                paths.add(RUST_MAC_LIBRARY_PATH);
                break;
            case C:
                paths.add(MAC_LIBRARY_PATH);
                break;
            case RUST:
                paths.add(RUST_MAC_LIBRARY_PATH);
                break;
            default:
                throw new IllegalStateException("Unsupported backend: " + backend);
        }
    }

    private static void addLinuxLibraryCandidates(List<String> paths, Backend backend, String osArch) {
        switch (backend) {
            case AUTO:
                paths.add(LEGACY_LINUX_LIBRARY_PATH);
                paths.add(RUST_LINUX_LIBRARY_PATH);
                break;
            case C:
                paths.add(LEGACY_LINUX_LIBRARY_PATH);
                break;
            case RUST:
                paths.add(RUST_LINUX_LIBRARY_PATH);
                break;
            default:
                throw new IllegalStateException("Unsupported backend: " + backend);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

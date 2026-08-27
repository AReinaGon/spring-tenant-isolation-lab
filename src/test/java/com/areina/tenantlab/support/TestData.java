package com.areina.tenantlab.support;

import java.util.UUID;

/**
 * Deterministic identifiers and constants used by the fixtures and the assertions.
 */
public final class TestData {

    public static final String TENANT_A = "tenant-a";
    public static final String TENANT_B = "tenant-b";

    public static final String ISSUER = "https://identity.example.test";
    public static final String AUDIENCE = "document-api";

    public static final UUID WORKSPACE_A = UUID.fromString("00000000-0000-0000-0000-00000000001a");
    public static final UUID WORKSPACE_B = UUID.fromString("00000000-0000-0000-0000-00000000001b");

    public static final UUID DOC_A = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    public static final UUID DOC_B = UUID.fromString("00000000-0000-0000-0000-0000000000ab");

    private TestData() {
    }
}

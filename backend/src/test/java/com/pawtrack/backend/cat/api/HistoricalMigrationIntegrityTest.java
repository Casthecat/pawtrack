package com.pawtrack.backend.cat.api;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HistoricalMigrationIntegrityTest {
    @Test
    void v1ThroughV7RemainUnchanged() throws Exception {
        // V1-V6: frozen portfolio tag; V7: completed P3.1 baseline. Normalize Git CRLF conversion.
        var frozen = Map.of(
                "V1__init.sql", "31bf1bb8041b825b292ad000ca81b81563dc62042801ec872ac3f2537385b828",
                "V2__create_adoption_table.sql", "b619cf4bd3111f20c74a463a433031608d8114763dc9173171ff6469043719ff",
                "V3__add_media_fields_to_cats.sql", "49d79467bb349b6998b4254617709b2cb464ac482fffc61edc93b3e381d2a1dd",
                "V4__adoption_review_metadata.sql", "29585cc959c1b007b41bd3520cacd8fb54e52a8c219270c7b1815158398a1716",
                "V5__create_care_records.sql", "54b49253c84af7e293c928c06ce40df7f680da7edb8601f8962f590aa235b07d",
                "V6__alert_resolution.sql", "419a70b3b7fe2eea3bcc8b318ee5770723633b17f0e7c8561701e212068c5f73",
                "V7__split_cat_status.sql", "039aaebef5028fcbffd970b5cfae6ce3ef84c23be1d689ca76fba1f715eb28c4");
        for (var entry : frozen.entrySet()) {
            try (var resource = getClass().getResourceAsStream("/db/migration/" + entry.getKey())) {
                assertNotNull(resource, entry.getKey());
                String sql = new String(resource.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
                String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(sql.getBytes(StandardCharsets.UTF_8)));
                assertEquals(entry.getValue(), digest, entry.getKey());
            }
        }
    }
}

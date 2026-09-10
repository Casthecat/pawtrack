package com.pawtrack.backend.cat.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pawtrack.backend.cat.domain.Cat;
import com.pawtrack.backend.cat.repo.CatRepository;
import com.pawtrack.backend.cat.service.CatImageStorage;
import com.pawtrack.backend.support.TestAccounts;
import com.pawtrack.backend.support.TestImages;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:upload-security;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test") @AutoConfigureMockMvc @DirtiesContext
class CatImageSecurityIntegrationTest {
    private static final Path ROOT = Path.of("target", "upload-security-" + UUID.randomUUID()).toAbsolutePath().normalize();
    private static final Set<Path> STORED = new HashSet<>();
    @DynamicPropertySource static void directory(DynamicPropertyRegistry properties) { properties.add("upload.path", ROOT::toString); }
    @Autowired MockMvc mvc;
    @Autowired CatRepository cats;
    @Autowired ObjectMapper json;
    private Long catId;

    @BeforeEach void setup() { catId = cats.saveAndFlush(new Cat("Portrait cat")).getId(); }
    @AfterAll static void cleanup() throws Exception {
        for (Path file : STORED) Files.deleteIfExists(file);
        Files.deleteIfExists(ROOT);
    }

    @ParameterizedTest @ValueSource(strings = {"jpeg", "png"})
    void validImagesAreReencodedNamedByServerAndPubliclyRetrievable(String format) throws Exception {
        String url = upload(new MockMultipartFile("file", "attacker-name.html", "image/" + format, TestImages.image(format)));
        String extension = format.equals("jpeg") ? "jpg" : "png";
        assertTrue(url.matches("uploads/[0-9a-f-]{36}\\." + extension));
        var file = ROOT.resolve(Path.of(url).getFileName());
        assertEquals(ROOT.toRealPath(), file.toRealPath().getParent());
        assertNotNull(javax.imageio.ImageIO.read(file.toFile()));
        mvc.perform(get("/" + url)).andExpect(status().isOk()).andExpect(content().contentType("image/" + format))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        assertEquals(url, cats.findById(catId).orElseThrow().getImageUrl());
        assertNotEquals(url, upload(new MockMultipartFile("file", "attacker-name.html", "image/" + format, TestImages.image(format))));
    }

    @Test void uploadRequiresStaffAndCsrf() throws Exception {
        var file = new MockMultipartFile("file", "portrait.png", "image/png", TestImages.image("png"));
        mvc.perform(multipart(path()).file(file)).andExpect(status().isUnauthorized());
        mvc.perform(multipart(path()).file(file).with(user("adopter").roles("ADOPTER")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(multipart(path()).file(file).with(user(TestAccounts.staff()))).andExpect(status().isForbidden());
        assertNull(cats.findById(catId).orElseThrow().getImageUrl());
    }

    @ParameterizedTest @ValueSource(strings = {"image/svg+xml", "text/html", "application/javascript", "image/gif"})
    void unsupportedTypesAreRejected(String type) throws Exception {
        rejected(new MockMultipartFile("file", "portrait.svg", type, "<svg onload='alert(1)'/>".getBytes()), 400);
    }

    @Test void disguisedNonImageAndMismatchedOrCorruptImageAreRejected() throws Exception {
        rejected(new MockMultipartFile("file", "portrait.jpg", "image/jpeg", "<html><script>alert(1)</script></html>".getBytes()), 400);
        rejected(new MockMultipartFile("file", "portrait.png", "image/png", TestImages.image("jpeg")), 400);
        rejected(new MockMultipartFile("file", "portrait.png", "image/png", Arrays.copyOf(TestImages.image("png"), 20)), 400);
        rejected(new MockMultipartFile("file", "portrait.png", "image/png", new byte[0]), 400);
    }

    @ParameterizedTest @ValueSource(strings = {"../../escape.html", "..\\..\\escape.svg", "C:\\outside\\escape.jpg"})
    void originalPathLikeFilenameNeverControlsDestination(String filename) throws Exception {
        String url = upload(new MockMultipartFile("file", filename, "image/png", TestImages.image("png")));
        assertTrue(url.matches("uploads/[0-9a-f-]{36}\\.png"));
        assertFalse(url.contains("escape"));
        assertEquals(ROOT.toRealPath(), ROOT.resolve(Path.of(url).getFileName()).toRealPath().getParent());
    }

    @Test void byteAndPixelLimitsRejectOversizedInput() throws Exception {
        rejected(new MockMultipartFile("file", "large.png", "image/png", new byte[CatImageStorage.MAX_BYTES + 1]), 413);
        rejected(new MockMultipartFile("file", "wide.png", "image/png", TestImages.image("png", 4097, 1)), 400);
    }

    @Test void reencodingRemovesAppendedScriptBytes() throws Exception {
        byte[] original = TestImages.image("png");
        byte[] script = "<script>ATTACKER_MARKER</script>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] polyglot = Arrays.copyOf(original, original.length + script.length);
        System.arraycopy(script, 0, polyglot, original.length, script.length);
        String url = upload(new MockMultipartFile("file", "polyglot.png", "image/png", polyglot));
        assertFalse(new String(Files.readAllBytes(ROOT.resolve(Path.of(url).getFileName())), java.nio.charset.StandardCharsets.ISO_8859_1).contains("ATTACKER_MARKER"));
    }

    private String path() { return "/api/cats/" + catId + "/upload-image"; }
    private String upload(MockMultipartFile file) throws Exception {
        var response = mvc.perform(multipart(path()).file(file).with(user(TestAccounts.staff())).with(csrf()))
                .andExpect(status().isOk()).andReturn().getResponse();
        String url = json.readTree(response.getContentAsString()).get("imageUrl").asText();
        STORED.add(ROOT.resolve(Path.of(url).getFileName()));
        return url;
    }
    private void rejected(MockMultipartFile file, int expected) throws Exception {
        mvc.perform(multipart(path()).file(file).with(user(TestAccounts.staff())).with(csrf()))
                .andExpect(status().is(expected)).andExpect(jsonPath("$.message").isNotEmpty());
        assertNull(cats.findById(catId).orElseThrow().getImageUrl());
    }
}

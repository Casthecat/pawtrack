package com.pawtrack.backend.cat.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pawtrack.backend.cat.domain.Cat;
import com.pawtrack.backend.cat.repo.CatRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CatImageUploadIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CatRepository catRepository;
    private Path imagePathForCleanup;

    @BeforeEach
    void ensureUploadDirectory() throws Exception {
        Files.createDirectories(Paths.get("uploads"));
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void uploadImage_storesFile_updatesEntity_and_servesStatic() throws Exception {

        Cat cat = new Cat("Kumo");
        cat.setStatus("NORMAL");
        Cat savedCat = catRepository.save(cat);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                new byte[]{1, 2, 3, 4}
        );

        MvcResult result = mockMvc.perform(
                        multipart("/api/cats/{id}/upload-image", savedCat.getId())
                                .file(file)
                                .contentType(MediaType.MULTIPART_FORM_DATA)
                )
                .andExpect(status().isOk())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(json);
        String imageUrl = root.get("imageUrl").asText();
        assertNotNull(imageUrl);

        Cat updated = catRepository.findById(savedCat.getId()).orElseThrow();
        assertTrue(updated.getImageUrl().contains("uploads/"));

        imagePathForCleanup = Paths.get(imageUrl);
        assertTrue(Files.exists(imagePathForCleanup));

        mockMvc.perform(get("/" + imageUrl))
                .andExpect(status().isOk());
    }

    @AfterEach
    void cleanupUploadedFile() throws Exception {
        if (imagePathForCleanup != null) {
            Files.deleteIfExists(imagePathForCleanup);
            imagePathForCleanup = null;
        }
    }

}

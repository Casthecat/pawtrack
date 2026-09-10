package com.pawtrack.backend.cat.api;

import com.pawtrack.backend.alert.service.AlertService;
import com.pawtrack.backend.cat.domain.Cat;
import com.pawtrack.backend.cat.service.CatService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CatController.class)
class CatControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CatService catService;

    @MockBean
    private AlertService alertService;

    @Test
    void getById_returnsCatResponse() throws Exception {
        Cat cat = new Cat("Mochi");
        cat.setStatus("NORMAL");

        when(catService.getById(1L)).thenReturn(cat);

        mockMvc.perform(get("/api/cats/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Mochi"))
                .andExpect(jsonPath("$.status").value("NORMAL"));
    }

    @Test
    void getById_notFound_returnsFriendlyError() throws Exception {
        when(catService.getById(99L)).thenThrow(new EntityNotFoundException("Cat not found: 99"));

        mockMvc.perform(get("/api/cats/{id}", 99L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Cat not found: 99"));
    }
}

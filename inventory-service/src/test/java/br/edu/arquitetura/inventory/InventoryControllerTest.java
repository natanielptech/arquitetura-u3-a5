package br.edu.arquitetura.inventory;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class InventoryControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void returnsStock() throws Exception {
        mvc.perform(get("/inventory/BOOK-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(100));
    }

    @Test
    void unknownSkuIs404() throws Exception {
        mvc.perform(get("/inventory/NOPE")).andExpect(status().isNotFound());
    }
}

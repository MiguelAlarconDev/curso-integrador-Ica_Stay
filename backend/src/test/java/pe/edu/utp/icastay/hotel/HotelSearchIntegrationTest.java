package pe.edu.utp.icastay.hotel;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = "icastay.security.jwt.secret=12345678901234567890123456789012")
@Transactional
class HotelSearchIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy security;
    @Autowired JdbcTemplate jdbc;
    MockMvc mvc;
    String marker;
    UUID active, inactive;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
        marker = "Catalog" + UUID.randomUUID().toString().replace("-", "");
        active = fixture(marker + " Oasis", "Ica", "ACTIVE");
        fixture(marker + " Oasis dos", "ICA", "ACTIVE");
        fixture(marker + " Costa", "Paracas", "ACTIVE");
        inactive = fixture(marker + " Oasis oculto", "Ica", "INACTIVE");
        fixture(marker + " 100%_literal", "Ica", "ACTIVE");
    }
    UUID fixture(String name, String city, String status) {
        UUID admin = UUID.randomUUID(), hotel = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,name,email,password_hash,role,status) VALUES (?,?,?,?,?,?)",
                admin, "Test", admin + "@example.test", "unused", "HOTEL_ADMIN", "ACTIVE");
        jdbc.update("INSERT INTO hotels(id,admin_user_id,name,address,city,status) VALUES (?,?,?,?,?,?)",
                hotel, admin, name, "Dirección de prueba", city, status);
        return hotel;
    }
    @Test void partialNameCaseAndInactiveExclusion() throws Exception {
        mvc.perform(get("/api/v1/hotels").param("name", marker.toLowerCase()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(4));
        mvc.perform(get("/api/v1/hotels/{id}", active)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/hotels/{id}", inactive)).andExpect(status().isNotFound());
    }
    @Test void combinedNormalizedFiltersAndTotals() throws Exception {
        mvc.perform(get("/api/v1/hotels").param("name", "  " + marker.toUpperCase() + " Oasis  ")
                .param("city", "  iCa  ").param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.content[0].name").value(marker + " Oasis dos"));
    }
    @Test void exactCityAndLiteralWildcards() throws Exception {
        mvc.perform(get("/api/v1/hotels").param("name", marker).param("city", "Ic"))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/hotels").param("name", marker + " 100%_"))
                .andExpect(jsonPath("$.totalElements").value(1));
        String uniqueCity = "City" + marker;
        fixture("Hotel de ciudad", uniqueCity, "ACTIVE");
        mvc.perform(get("/api/v1/hotels").param("city", uniqueCity.toLowerCase()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }
    @Test void emptyFiltersMatchUnfilteredResponse() throws Exception {
        String plain = mvc.perform(get("/api/v1/hotels")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/v1/hotels").param("name", "  ").param("city", ""))
                .andExpect(status().isOk()).andExpect(content().json(plain));
    }
    @Test void rejectsInvalidParameters() throws Exception {
        for (String[] pair : new String[][] { {"page", "-1"}, {"page", "2147483647"}, {"size", "0"}, {"size", "101"},
                {"name", "x".repeat(181)}, {"city", "x".repeat(121)}, {"page", "abc"} }) {
            mvc.perform(get("/api/v1/hotels").param(pair[0], pair[1])).andExpect(status().isBadRequest());
        }
    }
}

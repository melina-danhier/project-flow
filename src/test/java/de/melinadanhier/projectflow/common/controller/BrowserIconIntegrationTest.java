package de.melinadanhier.projectflow.common.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BrowserIconIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void servesLogoSvgAsStaticResource() throws Exception {
        mockMvc.perform(get("/images/logo.svg"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("image/svg+xml"))
                .andExpect(content().string(containsString("<svg")))
                .andExpect(content().string(containsString("points=\"12.5,12.5 19,19 12.5,25.5 6,19\"")));
    }

    @Test
    void servesFaviconSvgAsStaticResource() throws Exception {
        mockMvc.perform(get("/favicon.svg"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("image/svg+xml"))
                .andExpect(content().string(containsString("<svg")))
                .andExpect(content().string(containsString("points=\"12.5,12.5 19,19 12.5,25.5 6,19\"")));
    }

    @Test
    void htmlPagesIncludeSvgIconInHead() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<link rel=\"icon\" type=\"image/svg+xml\" href=\"/images/logo.svg\">")))
                .andExpect(content().string(containsString("<link rel=\"apple-touch-icon\" href=\"/images/logo.svg\">")));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<link rel=\"icon\" type=\"image/svg+xml\" href=\"/images/logo.svg\">")));
    }
}

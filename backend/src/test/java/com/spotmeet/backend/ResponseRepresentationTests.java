package com.spotmeet.backend;

import com.spotmeet.backend.controller.MembershipController;
import com.spotmeet.backend.dto.ValidationPatterns;
import com.spotmeet.backend.repository.OrganizationRepository;
import com.spotmeet.backend.service.MembershipService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ResponseRepresentationTests {
    @Test
    void accessRequestErrorsAreNotHtmlDocuments() throws Exception {
        MembershipService service = mock(MembershipService.class);
        when(service.requestAccess(anyString(), anyString()))
                .thenThrow(new IllegalArgumentException("Chave '<script>alert(1)</script>' não encontrada."));
        MembershipController controller = new MembershipController();
        ReflectionTestUtils.setField(controller, "membershipService", service);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        var response = mvc.perform(post("/api/access-requests")
                .principal(new UsernamePasswordAuthenticationToken("user@example.com", null))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_HTML)
                .content("{\"accessKey\":\"#example\"}"))
                .andReturn().getResponse();
        assertEquals(406, response.getStatus());
        assertFalse(response.getContentType() != null
                && MediaType.parseMediaType(response.getContentType()).isCompatibleWith(MediaType.TEXT_HTML));
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/json", "*/*", ""})
    void reflectedErrorTextIsJsonDataForSupportedConsumers(String accept) throws Exception {
        MembershipService service = mock(MembershipService.class);
        String message = "Chave '<script>alert(1)</script>' não encontrada.";
        when(service.requestAccess(anyString(), anyString())).thenThrow(new IllegalArgumentException(message));
        MembershipController controller = new MembershipController();
        ReflectionTestUtils.setField(controller, "membershipService", service);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        var request = post("/api/access-requests")
                .principal(new UsernamePasswordAuthenticationToken("user@example.com", null))
                .contentType(MediaType.APPLICATION_JSON).content("{\"accessKey\":\"#example\"}");
        if (!accept.isEmpty()) request.accept(accept);
        mvc.perform(request).andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value(message));
    }

    @Test
    void aMissingOrganizationDoesNotEchoTheUntrustedKey() {
        var organizations = mock(OrganizationRepository.class);
        when(organizations.findIdsByAccessKeyIgnoreCase(anyString(), eq(ValidationPatterns.ORGANIZATION_WHITESPACE)))
                .thenReturn(java.util.List.of());
        MembershipService service = new MembershipService();
        ReflectionTestUtils.setField(service, "organizationRepo", organizations);
        var exception = assertThrows(IllegalArgumentException.class,
                () -> service.requestAccess("user@example.com", "<script>alert(1)</script>"));
        assertEquals("Organização não encontrada. Verifique a chave e tente novamente.", exception.getMessage());
    }
}

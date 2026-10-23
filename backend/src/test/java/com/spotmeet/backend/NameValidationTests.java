package com.spotmeet.backend;

import com.spotmeet.backend.security.AuthenticationAttempts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotmeet.backend.controller.AuthController;
import com.spotmeet.backend.controller.UserController;
import com.spotmeet.backend.dto.CodeDeliveryResultDTO;
import com.spotmeet.backend.dto.RegisterRequestDTO;
import com.spotmeet.backend.dto.UpdateUserProfileDTO;
import com.spotmeet.backend.dto.UserProfileResponseDTO;
import com.spotmeet.backend.exception.GlobalExceptionHandler;
import com.spotmeet.backend.model.User;
import com.spotmeet.backend.service.UserService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NameValidationTests {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static ValidatorFactory factory;
    private static Validator validator;
    private static LocalValidatorFactoryBean mvcValidator;
    private static MockMvc mvc;
    private static UserService userService;

    record NameCase(String label, String input, String normalized, boolean valid) {}

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
        mvcValidator = new LocalValidatorFactoryBean();
        mvcValidator.afterPropertiesSet();
        userService = mock(UserService.class);
        AuthController authController = new AuthController();
        ReflectionTestUtils.setField(authController, "attempts", new AuthenticationAttempts());
        UserController userController = new UserController();
        ReflectionTestUtils.setField(authController, "userService", userService);
        ReflectionTestUtils.setField(userController, "userService", userService);
        mvc = MockMvcBuilders.standaloneSetup(authController, userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(mvcValidator)
                .build();
    }

    @BeforeEach
    void resetService() {
        reset(userService);
        when(userService.registerUser(any())).thenAnswer(invocation -> {
            RegisterRequestDTO dto = invocation.getArgument(0);
            User user = new User();
            user.setName(dto.getName());
            user.setEmail(dto.getEmail());
            CodeDeliveryResultDTO result = new CodeDeliveryResultDTO();
            result.setUser(user);
            result.setMessage("Cadastro realizado.");
            return result;
        });
        when(userService.updateMyProfile(anyString(), any())).thenAnswer(invocation -> {
            UpdateUserProfileDTO dto = invocation.getArgument(1);
            User user = new User();
            user.setName(dto.getName() == null ? "Existing User" : dto.getName());
            user.setEmail("user@example.com");
            return UserProfileResponseDTO.fromEntity(user);
        });
    }

    @AfterAll
    static void tearDown() {
        mvcValidator.close();
        factory.close();
    }

    static Stream<NameCase> nameCases() throws Exception {
        try (InputStream input = NameValidationTests.class.getResourceAsStream("/name-validation-cases.json")) {
            assertNotNull(input);
            return Arrays.stream(mapper.readValue(input, NameCase[].class));
        }
    }

    static Stream<NameCase> invalidNameCases() throws Exception {
        return nameCases().filter(nameCase -> !nameCase.valid());
    }

    static Stream<NameCase> validNameCases() throws Exception {
        return nameCases().filter(NameCase::valid);
    }

    private Map<String, String> registerBody(String name) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("email", "user@example.com");
        body.put("password", "Example1");
        return body;
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("nameCases")
    void normalizesBeforeValidatingBothDtos(NameCase nameCase) throws Exception {
        RegisterRequestDTO registerDto = mapper.readValue(mapper.writeValueAsBytes(registerBody(nameCase.input())), RegisterRequestDTO.class);
        UpdateUserProfileDTO profileDto = mapper.readValue(mapper.writeValueAsBytes(Map.of("name", nameCase.input())), UpdateUserProfileDTO.class);
        assertEquals(nameCase.normalized(), registerDto.getName());
        assertEquals(nameCase.normalized(), profileDto.getName());
        assertEquals(nameCase.valid(), validator.validateProperty(registerDto, "name").isEmpty(), "registration: " + nameCase.label());
        assertEquals(nameCase.valid(), validator.validateProperty(profileDto, "name").isEmpty(), "profile: " + nameCase.label());
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("invalidNameCases")
    void rejectsInvalidNamesBeforeCallingTheService(NameCase nameCase) throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(registerBody(nameCase.input()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").isNotEmpty());
        mvc.perform(put("/api/users/me")
                        .principal(new UsernamePasswordAuthenticationToken("user@example.com", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(Map.of("name", nameCase.input()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").isNotEmpty());
        verifyNoInteractions(userService);
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("validNameCases")
    void sendsNormalizedNamesToTheService(NameCase nameCase) throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(registerBody(nameCase.input()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(nameCase.normalized()));
        mvc.perform(put("/api/users/me")
                        .principal(new UsernamePasswordAuthenticationToken("user@example.com", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(Map.of("name", nameCase.input()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(nameCase.normalized()));
        verify(userService).registerUser(argThat(dto -> dto.getName().equals(nameCase.normalized())));
        verify(userService).updateMyProfile(eq("user@example.com"), argThat(dto -> dto.getName().equals(nameCase.normalized())));
    }

    @Test
    void requiresANameForRegistrationWhenMissingOrNull() throws Exception {
        Map<String, String> body = registerBody(null);
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsBytes(body)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.name").isNotEmpty());
            body.remove("name");
        }
        verifyNoInteractions(userService);
    }

    @Test
    void preservesProfileUpdatesWithoutChangingTheName() throws Exception {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("name", null);
        body.put("bio", "Updated bio");
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(put("/api/users/me")
                            .principal(new UsernamePasswordAuthenticationToken("user@example.com", null))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsBytes(body)))
                    .andExpect(status().isOk());
            body.remove("name");
        }
        verify(userService, times(2)).updateMyProfile(eq("user@example.com"),
                argThat(dto -> dto.getName() == null && dto.getBio().equals("Updated bio")));
    }
}

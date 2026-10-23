package com.spotmeet.backend;

import com.spotmeet.backend.security.AuthenticationAttempts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotmeet.backend.controller.AuthController;
import com.spotmeet.backend.controller.UserController;
import com.spotmeet.backend.dto.*;
import com.spotmeet.backend.exception.GlobalExceptionHandler;
import com.spotmeet.backend.model.User;
import com.spotmeet.backend.security.JwtUtil;
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
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OtpValidationTests {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static ValidatorFactory factory;
    private static Validator validator;
    private static LocalValidatorFactoryBean mvcValidator;
    private static MockMvc mvc;
    private static UserService userService;
    private static JwtUtil jwtUtil;

    record OtpCase(String label, String input, String normalized, boolean valid) {}
    record OtpRoute(String path, Class<?> dtoType, String property) {}

    private static final OtpRoute[] routes = {
            new OtpRoute("/api/auth/verify-email", VerifyEmailDTO.class, "code"),
            new OtpRoute("/api/auth/verify-recovery-code", VerifyRecoveryCodeDTO.class, "token"),
            new OtpRoute("/api/auth/reset-password", ResetPasswordDTO.class, "token"),
            new OtpRoute("/api/users/me/email-change/confirm", ConfirmEmailChangeDTO.class, "currentCode"),
            new OtpRoute("/api/users/me/email-change/complete", CompleteEmailChangeDTO.class, "newEmailCode")
    };

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
        mvcValidator = new LocalValidatorFactoryBean();
        mvcValidator.afterPropertiesSet();
        userService = mock(UserService.class);
        jwtUtil = mock(JwtUtil.class);
        AuthController authController = new AuthController();
        ReflectionTestUtils.setField(authController, "attempts", new AuthenticationAttempts());
        UserController userController = new UserController();
        ReflectionTestUtils.setField(authController, "userService", userService);
        ReflectionTestUtils.setField(authController, "jwtUtil", jwtUtil);
        ReflectionTestUtils.setField(userController, "userService", userService);
        ReflectionTestUtils.setField(userController, "jwtUtil", jwtUtil);
        mvc = MockMvcBuilders.standaloneSetup(authController, userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(mvcValidator)
                .build();
    }

    @BeforeEach
    void resetMocks() {
        reset(userService, jwtUtil);
    }

    @AfterAll
    static void tearDown() {
        mvcValidator.close();
        factory.close();
    }

    static Stream<OtpCase> otpCases() throws Exception {
        try (InputStream input = OtpValidationTests.class.getResourceAsStream("/otp-validation-cases.json")) {
            assertNotNull(input);
            return Arrays.stream(mapper.readValue(input, OtpCase[].class));
        }
    }

    static Stream<OtpCase> invalidOtpCases() throws Exception {
        return otpCases().filter(otpCase -> !otpCase.valid());
    }

    static Stream<OtpCase> validOtpCases() throws Exception {
        return otpCases().filter(OtpCase::valid);
    }

    private Map<String, String> requestBody(OtpRoute route, String code) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put(route.property(), code);
        body.put("email", "user@example.com");
        if (route.dtoType() == ResetPasswordDTO.class) {
            body.put("newPassword", "Example1");
        } else if (route.dtoType() == ConfirmEmailChangeDTO.class) {
            body.put("newEmail", "new@example.com");
        }
        return body;
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("otpCases")
    void validatesCodeInEveryDto(OtpCase otpCase) throws Exception {
        for (OtpRoute route : routes) {
            Object dto = route.dtoType().getDeclaredConstructor().newInstance();
            ReflectionTestUtils.setField(dto, route.property(), otpCase.input());
            boolean accepted = validator.validateProperty(dto, route.property()).isEmpty();
            assertEquals(otpCase.valid(), accepted, route.path() + ": " + otpCase.label());
        }
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("invalidOtpCases")
    void rejectsInvalidCodeBeforeCallingTheService(OtpCase otpCase) throws Exception {
        for (OtpRoute route : routes) {
            mvc.perform(post(route.path())
                            .principal(new UsernamePasswordAuthenticationToken("user@example.com", null))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsBytes(requestBody(route, otpCase.input()))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors." + route.property()).isNotEmpty());
        }
        verifyNoInteractions(userService, jwtUtil);
    }

    @Test
    void rejectsNullAndMissingCodes() throws Exception {
        for (OtpRoute route : routes) {
            Map<String, String> body = requestBody(route, null);
            for (int attempt = 0; attempt < 2; attempt++) {
                mvc.perform(post(route.path())
                                .principal(new UsernamePasswordAuthenticationToken("user@example.com", null))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsBytes(body)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.errors." + route.property()).isNotEmpty());
                body.remove(route.property());
            }
        }
        verifyNoInteractions(userService, jwtUtil);
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("validOtpCases")
    void passesSixDigitsToTheServiceWithoutLosingZeroes(OtpCase otpCase) throws Exception {
        User verifiedUser = new User();
        verifiedUser.setId(1L);
        verifiedUser.setName("Test User");
        verifiedUser.setEmail("user@example.com");
        verifiedUser.setRole("USER");
        verifiedUser.setEmailVerified(true);
        when(userService.verifyEmail(any())).thenReturn(verifiedUser);
        when(userService.verifyRecoveryCode(any())).thenReturn("Código validado.");
        when(userService.resetPassword(any())).thenReturn("Senha redefinida.");
        CodeDeliveryResultDTO result = new CodeDeliveryResultDTO("Verifique o seu e-mail.");
        result.setDestination("new@example.com");
        when(userService.confirmEmailChange(anyString(), any())).thenReturn(result);
        User user = new User();
        user.setEmail("new@example.com");
        when(userService.completeEmailChange(anyString(), any())).thenReturn(user);
        when(jwtUtil.extractIssuedAt("previous-test-session")).thenReturn(Instant.now());
        when(jwtUtil.generateToken(anyString(), any(), any())).thenReturn("test-session");

        for (OtpRoute route : routes) {
            mvc.perform(post(route.path())
                            .header("Authorization", "Bearer previous-test-session")
                            .principal(new UsernamePasswordAuthenticationToken("user@example.com", null))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsBytes(requestBody(route, otpCase.input()))))
                    .andExpect(status().isOk());
        }

        verify(userService).verifyEmail(argThat(dto -> dto.getCode().equals(otpCase.input())));
        verify(userService).verifyRecoveryCode(argThat(dto -> dto.getToken().equals(otpCase.input())));
        verify(userService).resetPassword(argThat(dto -> dto.getToken().equals(otpCase.input())));
        verify(userService).confirmEmailChange(eq("user@example.com"),
                argThat(dto -> dto.getCurrentCode().equals(otpCase.input())));
        verify(userService).completeEmailChange(eq("user@example.com"),
                argThat(dto -> dto.getNewEmailCode().equals(otpCase.input())));
    }
}

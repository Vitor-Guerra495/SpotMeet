package com.spotmeet.backend;

import com.spotmeet.backend.security.AuthenticationAttempts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotmeet.backend.controller.AuthController;
import com.spotmeet.backend.controller.UserController;
import com.spotmeet.backend.dto.*;
import com.spotmeet.backend.exception.GlobalExceptionHandler;
import com.spotmeet.backend.service.UserService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EmailValidationTests {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static ValidatorFactory factory;
    private static Validator validator;
    private static LocalValidatorFactoryBean mvcValidator;
    private static MockMvc mvc;
    private static UserService userService;

    record EmailCase(String label, String input, String normalized, boolean valid) {}
    record EmailRoute(String path, Class<?> dtoType, String property) {}

    private static final EmailRoute[] routes = {
            new EmailRoute("/api/auth/login", LoginRequestDTO.class, "email"),
            new EmailRoute("/api/auth/register", RegisterRequestDTO.class, "email"),
            new EmailRoute("/api/auth/forgot-password", ForgotPasswordDTO.class, "email"),
            new EmailRoute("/api/auth/resend-verification", ResendVerificationDTO.class, "email"),
            new EmailRoute("/api/auth/verify-email", VerifyEmailDTO.class, "email"),
            new EmailRoute("/api/auth/verify-recovery-code", VerifyRecoveryCodeDTO.class, "email"),
            new EmailRoute("/api/auth/reset-password", ResetPasswordDTO.class, "email"),
            new EmailRoute("/api/users/me/email-change/confirm", ConfirmEmailChangeDTO.class, "newEmail")
    };

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

    @AfterAll
    static void tearDown() {
        mvcValidator.close();
        factory.close();
    }

    static Stream<EmailCase> emailCases() throws Exception {
        try (InputStream input = EmailValidationTests.class.getResourceAsStream("/email-validation-cases.json")) {
            assertNotNull(input);
            return Arrays.stream(mapper.readValue(input, EmailCase[].class));
        }
    }

    static Stream<EmailCase> invalidEmailCases() throws Exception {
        return emailCases().filter(emailCase -> !emailCase.valid());
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("emailCases")
    void validatesEmailInEveryDto(EmailCase emailCase) throws Exception {
        for (EmailRoute route : routes) {
            Object dto = route.dtoType().getDeclaredConstructor().newInstance();
            ReflectionTestUtils.setField(dto, route.property(), emailCase.normalized());
            boolean accepted = validator.validateProperty(dto, route.property()).isEmpty();
            assertEquals(emailCase.valid(), accepted, route.path() + ": " + emailCase.label());
        }
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("invalidEmailCases")
    void rejectsInvalidEmailBeforeCallingTheService(EmailCase emailCase) throws Exception {
        for (EmailRoute route : routes) {
            Map<String, String> body = new LinkedHashMap<>();
            body.put(route.property(), emailCase.normalized());
            if (route.dtoType() == LoginRequestDTO.class) {
                body.put("password", "Example1");
            } else if (route.dtoType() == RegisterRequestDTO.class) {
                body.put("name", "Test User");
                body.put("password", "Example1");
            } else if (route.dtoType() == VerifyEmailDTO.class) {
                body.put("code", "123456");
            } else if (route.dtoType() == VerifyRecoveryCodeDTO.class) {
                body.put("token", "123456");
            } else if (route.dtoType() == ResetPasswordDTO.class) {
                body.put("token", "123456");
                body.put("newPassword", "Example1");
            } else if (route.dtoType() == ConfirmEmailChangeDTO.class) {
                body.put("currentCode", "123456");
            }

            mvc.perform(post(route.path())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsBytes(body)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors." + route.property()).isNotEmpty());
        }
        verifyNoInteractions(userService);
    }
}

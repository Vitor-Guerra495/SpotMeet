package com.spotmeet.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Downloads the public keys Google uses to sign ID tokens. */
@Component
public class GoogleKeysClient {

    private static final String KEYS_URL = "https://www.googleapis.com/oauth2/v3/certs";

    private final RestClient http = RestClient.create();

    public JsonNode fetch() {
        return http.get().uri(KEYS_URL).retrieve().body(JsonNode.class);
    }
}

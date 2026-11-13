package com.spotmeet.backend.dto;

/** Organization shown in the list of the Entrar tab. */
public record ListedOrganizationDTO(Long id, String name, String accessKey, String ownerName, long memberCount) {
}

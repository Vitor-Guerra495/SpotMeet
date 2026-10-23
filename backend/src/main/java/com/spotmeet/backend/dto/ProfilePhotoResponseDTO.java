package com.spotmeet.backend.dto;

public record ProfilePhotoResponseDTO(Long userId, String imageBase64, String version) {
}

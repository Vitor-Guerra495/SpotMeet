package com.spotmeet.backend.dto;

import jakarta.validation.constraints.Size;

/** Optional comment sent with the answer to a meeting invitation. */
public class MeetingAnswerDTO {

    @Size(max = 200, message = "O comentário não pode exceder 200 caracteres.")
    private String comment;

    public MeetingAnswerDTO() {}

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}

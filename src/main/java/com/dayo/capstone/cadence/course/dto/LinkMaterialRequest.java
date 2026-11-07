package com.dayo.capstone.cadence.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// A link material, typically a Google Drive file or folder.
@Getter
@Setter
public class LinkMaterialRequest {

    @NotBlank(message = "Give the material a title")
    @Size(max = 200, message = "The title can be at most 200 characters")
    private String title;

    @Size(max = 2000, message = "The note can be at most 2000 characters")
    private String note;

    @NotBlank(message = "Paste the link")
    @Size(max = 2000, message = "The link is too long")
    @Pattern(regexp = "^https?://\\S+$", message = "Enter a full link starting with https://")
    private String url;
}

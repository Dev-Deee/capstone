package com.dayo.capstone.cadence.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

// Creating or editing a course: what it is and who it is for.
@Getter
@Setter
public class CourseRequest {

    @NotBlank(message = "Give the course a title")
    @Size(max = 200, message = "The title can be at most 200 characters")
    private String title;

    @Size(max = 5000, message = "The description can be at most 5000 characters")
    private String description;

    private String subjectPublicId;

    // Every student in the institution, or only these cohorts.
    private boolean allStudents;

    private List<String> cohortPublicIds = new ArrayList<>();
}

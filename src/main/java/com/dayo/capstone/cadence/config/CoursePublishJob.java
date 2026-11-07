package com.dayo.capstone.cadence.config;

import com.dayo.capstone.cadence.course.enums.CourseStatus;
import com.dayo.capstone.cadence.course.model.Course;
import com.dayo.capstone.cadence.course.repository.CourseRepository;
import com.dayo.capstone.cadence.course.service.CourseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Publish later: every minute, scheduled courses whose time has passed go out. */
@Slf4j
@Component
@RequiredArgsConstructor
public class CoursePublishJob {

    private final CourseRepository courseRepository;
    private final CourseService courseService;

    @Scheduled(cron = "30 * * * * *")
    @Transactional
    public void publishDue() {
        List<Course> due = courseRepository.findByStatusAndPublishAtLessThanEqual(CourseStatus.DRAFT, LocalDateTime.now());
        due.forEach(c -> courseService.publishNow(c, null));
        if (!due.isEmpty()) log.info("CoursePublishJob: published {} scheduled course(s)", due.size());
    }
}

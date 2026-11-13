package com.spotmeet.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables the hourly task that adds the next dates of repeated meetings (MeetingService.extendSeries). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}

package com.fleetpilot.energy.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** The fleet is assessed at startup (once the other services answer) and every night, after the telemetry. */
@Component
@ConditionalOnProperty(name = "fleetpilot.energy.jobs-enabled", havingValue = "true", matchIfMissing = true)
public class TransitionJobs {

    private static final Logger LOG = LoggerFactory.getLogger(TransitionJobs.class);

    private final TransitionService transition;

    public TransitionJobs(TransitionService transition) {
        this.transition = transition;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void assessAtStartup() {
        Thread.ofVirtual().name("energy-assessment").start(() -> {
            for (int attempt = 1; attempt <= 60; attempt++) {
                try {
                    transition.refresh();
                    return;
                } catch (RuntimeException e) {
                    LOG.info("Fleet not assessed yet (attempt {}): {}", attempt, e.getMessage());
                    try {
                        Thread.sleep(10_000);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        });
    }

    @Scheduled(cron = "0 30 4 * * *", zone = "Europe/Paris")
    public void assessEveryNight() {
        transition.refresh();
    }
}

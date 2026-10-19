package com.fleetpilot.telemetry.api.rest;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.fleetpilot.telemetry.application.TelemetryViews.LiveState;
import com.fleetpilot.telemetry.application.TelemetryViews.TelemetryReader;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Pushes the positions of the connected vehicles to the open maps every two seconds (Server-Sent Events). Each map
 * only receives the vehicles of the company it may see; the states are read once per push for every subscriber.
 */
@Component
public class LiveStream {

    /** A map open in a browser, and the company it is restricted to (null: every company). */
    private record Subscriber(SseEmitter emitter, UUID companyId) {
    }

    private static final long TIMEOUT_MS = 30L * 60 * 1000;

    private final TelemetryReader reader;
    private final Map<SseEmitter, Subscriber> subscribers = new ConcurrentHashMap<>();

    public LiveStream(TelemetryReader reader) {
        this.reader = reader;
    }

    public SseEmitter subscribe(UUID companyId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        Subscriber subscriber = new Subscriber(emitter, companyId);
        subscribers.put(emitter, subscriber);
        emitter.onCompletion(() -> subscribers.remove(emitter));
        emitter.onTimeout(() -> subscribers.remove(emitter));
        emitter.onError(error -> subscribers.remove(emitter));
        send(subscriber, reader.live(companyId));
        return emitter;
    }

    @Scheduled(fixedRate = 2_000)
    public void push() {
        if (subscribers.isEmpty()) {
            return;
        }
        List<LiveState> all = reader.live(null);
        for (Subscriber subscriber : List.copyOf(subscribers.values())) {
            send(subscriber, subscriber.companyId() == null ? all
                    : all.stream().filter(s -> Objects.equals(s.companyId(), subscriber.companyId())).toList());
        }
    }

    int subscribers() {
        return subscribers.size();
    }

    private void send(Subscriber subscriber, List<LiveState> states) {
        try {
            subscriber.emitter().send(SseEmitter.event().name("states").data(states));
        } catch (IOException | IllegalStateException e) {
            // the browser went away: forget it
            subscribers.remove(subscriber.emitter());
        }
    }
}

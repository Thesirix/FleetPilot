package com.fleetpilot.driver.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The checklist of an inspection, in the order the driver walks around the vehicle. Every point is answered; a
 * damaged or missing point says what is wrong (the server refuses it otherwise).
 */
public final class Checklist {

    public static final List<String> ITEMS = List.of("BODYWORK", "WINDSCREEN", "TYRES", "LIGHTS", "INTERIOR",
            "CLEANLINESS", "SAFETY_KIT", "DOCUMENTS");

    public static final String OK = "OK";
    public static final String DAMAGED = "DAMAGED";
    public static final String MISSING = "MISSING";

    public record Answer(String condition, String comment) {
    }

    private final Map<String, Answer> answers = new LinkedHashMap<>();

    public Checklist() {
        ITEMS.forEach(item -> answers.put(item, new Answer(OK, "")));
    }

    public void set(String item, String condition) {
        Answer current = answers.get(item);
        answers.put(item, new Answer(condition, OK.equals(condition) ? "" : current.comment()));
    }

    public void comment(String item, String comment) {
        answers.put(item, new Answer(answers.get(item).condition(), comment == null ? "" : comment));
    }

    public Answer get(String item) {
        return answers.get(item);
    }

    public List<String> defects() {
        List<String> defects = new ArrayList<>();
        answers.forEach((item, answer) -> {
            if (!OK.equals(answer.condition())) {
                defects.add(item);
            }
        });
        return defects;
    }

    /** Points damaged or missing without a word on what is wrong. */
    public List<String> missingComments() {
        return defects().stream().filter(item -> answers.get(item).comment().isBlank()).collect(Collectors.toList());
    }

    public boolean complete() {
        return missingComments().isEmpty();
    }

    public Map<String, Answer> answers() {
        return Map.copyOf(answers);
    }
}

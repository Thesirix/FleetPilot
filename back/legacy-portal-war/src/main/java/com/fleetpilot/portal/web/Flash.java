package com.fleetpilot.portal.web;

import java.io.Serializable;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/** A message shown once on the next page, after a redirect (post, redirect, get). */
public record Flash(String kind, String text) implements Serializable {

    private static final String KEY = "flash";

    public static void success(HttpServletRequest request, String text) {
        request.getSession(true).setAttribute(KEY, new Flash("success", text));
    }

    public static void error(HttpServletRequest request, String text) {
        request.getSession(true).setAttribute(KEY, new Flash("error", text));
    }

    /** The message waiting for this page, removed from the session. */
    public static Flash take(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Flash flash = (Flash) session.getAttribute(KEY);
        session.removeAttribute(KEY);
        return flash;
    }
}

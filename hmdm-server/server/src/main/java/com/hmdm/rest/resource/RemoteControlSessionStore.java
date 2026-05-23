package com.hmdm.rest.resource;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class RemoteControlSessionStore {
    private static final long SESSION_TTL_MS = 15 * 60 * 1000;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Map<String, RemoteSession> SESSIONS = new ConcurrentHashMap<>();

    public static RemoteSession create(String number) {
        RemoteSession session = new RemoteSession(number, newToken());
        SESSIONS.put(number, session);
        return session;
    }

    public static RemoteSession active(String number) {
        RemoteSession session = SESSIONS.get(number);
        if (session == null) {
            return null;
        }
        if (System.currentTimeMillis() - session.createdAt > SESSION_TTL_MS) {
            SESSIONS.remove(number);
            return null;
        }
        return session;
    }

    public static RemoteSession remove(String number) {
        return SESSIONS.remove(number);
    }

    private static String newToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static class RemoteFrame {
        public String token;
        public String frame;
        public int width;
        public int height;
    }

    public static class RemoteCommand {
        public long id;
        public String type;
        public float x;
        public float y;
        public float x2;
        public float y2;
        public long duration;
        public String text;
    }

    public static class RemoteSession {
        public final String number;
        public final String token;
        public final long createdAt;
        public volatile String frame;
        public volatile int width;
        public volatile int height;
        public volatile long lastFrameTime;
        public final Queue<RemoteCommand> commands = new ConcurrentLinkedQueue<>();

        private RemoteSession(String number, String token) {
            this.number = number;
            this.token = token;
            this.createdAt = System.currentTimeMillis();
        }

        public RemoteSessionView toView() {
            RemoteSessionView view = new RemoteSessionView();
            view.number = number;
            view.token = token;
            view.frame = frame;
            view.width = width;
            view.height = height;
            view.createdAt = createdAt;
            view.lastFrameTime = lastFrameTime;
            view.active = true;
            return view;
        }
    }

    public static class RemoteSessionView {
        public String number;
        public String token;
        public String frame;
        public int width;
        public int height;
        public long createdAt;
        public long lastFrameTime;
        public boolean active;
    }
}

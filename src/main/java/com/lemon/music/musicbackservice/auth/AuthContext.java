package com.lemon.music.musicbackservice.auth;

public final class AuthContext {

    private static final ThreadLocal<UserSession> HOLDER = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(UserSession session) {
        HOLDER.set(session);
    }

    public static UserSession get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}

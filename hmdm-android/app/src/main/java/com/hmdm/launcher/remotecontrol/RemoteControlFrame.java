package com.hmdm.launcher.remotecontrol;

public class RemoteControlFrame {
    private String token;
    private String frame;
    private int width;
    private int height;

    public RemoteControlFrame(String token, String frame, int width, int height) {
        this.token = token;
        this.frame = frame;
        this.width = width;
        this.height = height;
    }

    public String getToken() {
        return token;
    }

    public String getFrame() {
        return frame;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}

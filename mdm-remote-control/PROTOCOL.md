# Remote Control Protocol

The first implementation uses the existing HTTP server as a relay.

1. The laptop dashboard creates a session for a device.
2. The server sends a push message to the Android agent with a short-lived session token.
3. The Android agent starts a visible foreground remote-control service.
4. The agent captures screen frames and posts JPEG frames to the server.
5. The dashboard polls the latest frame and displays it.
6. Dashboard pointer commands are queued on the server.
7. The Android agent polls commands and performs gestures through Accessibility Service.

This avoids adding WebRTC infrastructure in the first version and fits the existing Docker/Tomcat setup.


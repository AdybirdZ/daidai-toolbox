package org.daidai.modules.messaging;

/** Common host-facing contract for LAN and Bluetooth transports. */
public interface MessagePeer extends AutoCloseable {
    interface Listener {
        void state(String state);
        void received(String text);
        void sent(String text);
    }
    boolean connected();
    void send(String text);
    @Override void close();
}

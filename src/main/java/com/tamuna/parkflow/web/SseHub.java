package com.tamuna.parkflow.web;

import com.tamuna.parkflow.model.ParkingEvent;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;

public final class SseHub {
    private final CopyOnWriteArrayList<SseEmitter> clients = new CopyOnWriteArrayList<>();

    public SseEmitter connect(DashboardState initialState) {
        SseEmitter emitter = new SseEmitter(0L);
        clients.add(emitter);
        emitter.onCompletion(() -> clients.remove(emitter));
        emitter.onTimeout(() -> clients.remove(emitter));
        emitter.onError(error -> clients.remove(emitter));
        try {
            emitter.send(SseEmitter.event()
                    .name("state")
                    .data(initialState)
                    .reconnectTime(1_500));
        } catch (IOException error) {
            clients.remove(emitter);
            emitter.completeWithError(error);
        }
        return emitter;
    }

    public void broadcastState(DashboardState state) {
        broadcast("state", state);
    }

    public void broadcastEvent(ParkingEvent event) {
        broadcast("activity", event);
    }

    private void broadcast(String name, Object payload) {
        for (SseEmitter emitter : clients) {
            try {
                emitter.send(SseEmitter.event().name(name).data(payload));
            } catch (IOException | IllegalStateException error) {
                clients.remove(emitter);
                emitter.complete();
            }
        }
    }
}

package com.tamuna.parkflow;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.CookieManager;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ParkFlowWebApplicationTest {
    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newBuilder()
            .cookieHandler(new CookieManager())
            .build();

    @Test
    void servesDashboardAndSimulationApi() throws Exception {
        HttpResponse<String> page = client.send(
                HttpRequest.newBuilder(uri("/")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("ParkFlow"));

        HttpResponse<String> state = client.send(
                HttpRequest.newBuilder(uri("/api/state")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, state.statusCode());
        assertTrue(state.body().contains("\"capacity\":36"));
        assertTrue(state.body().contains("\"status\":\"READY\""));

        HttpResponse<String> started = client.send(
                HttpRequest.newBuilder(uri("/api/simulation/start"))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, started.statusCode());
        assertTrue(started.body().contains("\"status\":\"RUNNING\""));

        HttpResponse<String> runningState = client.send(
                HttpRequest.newBuilder(uri("/api/state")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertTrue(runningState.body().contains("\"status\":\"RUNNING\""));

        HttpClient anotherVisitor = HttpClient.newBuilder()
                .cookieHandler(new CookieManager())
                .build();
        HttpResponse<String> isolatedState = anotherVisitor.send(
                HttpRequest.newBuilder(uri("/api/state")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertTrue(isolatedState.body().contains("\"status\":\"READY\""));
        assertTrue(isolatedState.body().contains("\"occupied\":0"));
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }
}

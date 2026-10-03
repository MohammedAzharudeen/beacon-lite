package com.beacon.testsupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.GZIPInputStream;

/**
 * Serves a store recording made by {@code scripts/record-stores.py} (manifest.json + gzip bodies)
 * on a local port, answering each recorded path with the store's real status, content type and
 * body. Paths that weren't recorded answer 404.
 */
public final class RecordedStoreServer implements AutoCloseable {

  private final HttpServer server;
  private final Map<String, Recorded> byPath = new HashMap<>();
  private final List<String> requested = new CopyOnWriteArrayList<>();

  private RecordedStoreServer(Path dir) throws IOException {
    JsonNode manifest = new ObjectMapper().readTree(dir.resolve("manifest.json").toFile());
    for (JsonNode r : manifest.path("requests")) {
      if (r.hasNonNull("file")) {
        byPath.put(
            r.path("path").asText(),
            new Recorded(
                r.path("status").asInt(),
                r.path("contentType").asText(),
                dir.resolve(r.path("file").asText())));
      }
    }
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/",
        exchange -> {
          URI uri = exchange.getRequestURI();
          String path =
              uri.getRawQuery() == null
                  ? uri.getRawPath()
                  : uri.getRawPath() + "?" + uri.getRawQuery();
          requested.add(path);
          Recorded rec = byPath.get(path);
          try (exchange) {
            if (rec == null) {
              exchange.sendResponseHeaders(404, -1);
              return;
            }
            byte[] body;
            try (InputStream in = new GZIPInputStream(Files.newInputStream(rec.file()))) {
              body = in.readAllBytes();
            }
            exchange.getResponseHeaders().add("Content-Type", rec.contentType());
            exchange.sendResponseHeaders(rec.status(), body.length == 0 ? -1 : body.length);
            if (body.length > 0) {
              try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
              }
            }
          }
        });
    server.start();
  }

  public static RecordedStoreServer start(Path dir) {
    try {
      return new RecordedStoreServer(dir);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public URI baseUri() {
    return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
  }

  /** Paths requested so far, in order. */
  public List<String> requested() {
    return List.copyOf(requested);
  }

  @Override
  public void close() {
    server.stop(0);
  }

  private record Recorded(int status, String contentType, Path file) {}
}

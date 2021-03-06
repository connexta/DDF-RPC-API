package com.connexta.jsonrpc.impl;

import static com.connexta.util.MapFactory.mapOf;

import com.connexta.jsonrpc.Method;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsonRpcHttpServlet extends HttpServlet {
  private static final Logger LOGGER = LoggerFactory.getLogger(JsonRpcHttpServlet.class);
  private static final Gson GSON =
      new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").create();

  private Method method;

  public JsonRpcHttpServlet(Method method) {
    this.method = method;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    resp.setHeader("Content-Type", "application/json");
    Map<String, Object> request = mapOf("id", 0, "method", "list-methods", "params", mapOf());
    Object methods = method.apply(request);
    try (Writer writer = getWriter(req, resp)) {
      GSON.toJson(methods, writer);
    }
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    resp.setHeader("Content-Type", "application/json");

    String payload = req.getReader().lines().collect(Collectors.joining("\n"));
    LOGGER.trace("Got request:\n{}", payload);
    Object request = GSON.fromJson(payload, Object.class);
    Object response;

    if (request instanceof List) {
      List<Map<String, Object>> requests = (List<Map<String, Object>>) request;
      response = requests.stream().map(method::apply).collect(Collectors.toList());
    } else {
      response = method.apply((Map<String, Object>) request);
    }
    if (LOGGER.isTraceEnabled()) {
      Gson pprintGson =
          new GsonBuilder()
              .disableHtmlEscaping()
              .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
              .setPrettyPrinting()
              .create();
      LOGGER.trace("Sending response:\n{}", pprintGson.toJson(response));
    }
    try (Writer writer = getWriter(req, resp)) {
      GSON.toJson(response, writer);
    }
  }

  private Writer getWriter(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    boolean wantsGzip =
        Optional.of(req)
            .map(r -> r.getHeader("Accept-Encoding"))
            .map(s -> s.contains("gzip"))
            .orElse(false);

    if (!wantsGzip) {
      return resp.getWriter();
    }

    resp.addHeader("Content-Encoding", "gzip");
    return new OutputStreamWriter(new GZIPOutputStream(resp.getOutputStream(), true));
  }
}

package com.github.spud.tinystore.infrastructure.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.springframework.boot.json.AbstractJsonParser;
import org.springframework.boot.json.JsonParseException;

public class JsonUtils extends AbstractJsonParser {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  private static final JsonUtils INSTANCE = new JsonUtils();

  public static ObjectMapper objectMapper() {
    return objectMapper;
  }

  public static <T> T tryParseObject(Callable<T> parser, Class<? extends Exception> check) {
    return INSTANCE.tryParse(parser, check);
  }

  public static JsonNode readTree(String json) {
    return INSTANCE.tryParse(() -> objectMapper.readTree(json), Exception.class);
  }

  public static String toJson(Object obj) {
    return INSTANCE.tryParse(() -> objectMapper.writeValueAsString(obj), Exception.class);
  }

  public static <T> T fromJson(String json, Class<T> clazz) {
    return INSTANCE.tryParse(() -> objectMapper.readValue(json, clazz), Exception.class);
  }

  public static <T> T fromJson(String json, TypeReference<T> typeReference) {
    return INSTANCE.tryParse(() -> objectMapper.readValue(json, typeReference), Exception.class);
  }

  @Override
  public Map<String, Object> parseMap(String json) throws JsonParseException {
    return fromJson(json, new TypeReference<>() {
    });
  }

  @Override
  public List<Object> parseList(String json) throws JsonParseException {
    return fromJson(json, new TypeReference<>() {
    });
  }
}

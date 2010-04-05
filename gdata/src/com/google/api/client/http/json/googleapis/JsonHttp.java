/*
 * Copyright (c) 2010 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.api.client.http.json.googleapis;

import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.json.Json;
import com.google.api.client.json.googleapis.JsonFeedParser;
import com.google.api.client.json.googleapis.JsonMultiKindFeedParser;

import org.codehaus.jackson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

public class JsonHttp {

  static final Logger LOGGER = Logger.getLogger(JsonHttp.class.getName());

  public static <T, I> JsonFeedParser<T, I> useFeedParser(
      HttpResponse response, Class<T> feedClass, Class<I> itemClass)
      throws IOException {
    JsonParser parser = JsonHttp.processAsJsonParser(response);
    return new JsonFeedParser<T, I>(parser, feedClass, itemClass);
  }

  public static <T, I> JsonMultiKindFeedParser<T> useMultiKindFeedParser(
      HttpResponse response, Class<T> feedClass, Class<?>... itemClasses)
      throws IOException {
    return new JsonMultiKindFeedParser<T>(JsonHttp
        .processAsJsonParser(response), feedClass, itemClasses);
  }

  public static <T> T parse(HttpResponse response,
      Class<T> classToInstantiateAndParse) throws IOException {
    JsonParser parser = processAsJsonParser(response);
    return Json.parseAndClose(parser, classToInstantiateAndParse, null);
  }

  public static JsonParser processAsJsonParser(HttpResponse response)
      throws IOException {
    InputStream content = processAsInputStream(response);
    try {
      // check for JSON content type
      String contentType = response.getContentType();
      if (!contentType.startsWith(Json.CONTENT_TYPE)) {
        throw new IllegalArgumentException("Wrong content type: expected <"
            + Json.CONTENT_TYPE + "> but got <" + contentType + ">");
      }
      JsonParser parser = Json.JSON_FACTORY.createJsonParser(content);
      content = null;
      parser.nextToken();
      Json.skipToKey(parser, "data");
      return parser;
    } finally {
      if (content != null) {
        content.close();
      }
    }
  }

  public static InputStream processAsInputStream(HttpResponse response)
      throws IOException {
    if (response.isSuccessStatusCode()) {
      return response.getContent();
    }
    // TODO: use err=json (or jsonc?) to force error response to json-c?
    throw new HttpResponseException(response);
  }
}
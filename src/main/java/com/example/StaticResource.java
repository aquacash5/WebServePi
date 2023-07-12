package com.example;
/*
*****************************************************************************
* Class: StaticResource
*
* Purpose:
* A tool to host static resources bundled into the jar using jdk.httpserver.
*
*****************************************************************************
* Note: All revisions must include this caveat and the below code header including
*       Initial History up to and including Version 1.0:
*****************************************************************************
*  Initial History :
*
*      DATE          by whom                   for what
*     --------------+----------------------+---------------------------------
*     11/07/2023     Kyle J. Bloom          Initial implementation
* © Copyright 2023   Kyle J. Bloom
* Version 1.0
*****************************************************************************
*  Revisions:
*      DATE          by whom                   for what
*     --------------+----------------------+---------------------------------
*     MM/DD/YYYY     MyName OrInitials      Next update history goes here
* Version X.X starts here
*
*****************************************************************************
*/

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.FilenameUtils;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class StaticResource implements HttpHandler {
  private byte[] data;
  private String contentType;
  private boolean isDebug = false;

  @Override
  public void handle(HttpExchange t) throws IOException {
    if (this.isDebug)
      System.out.println("Request URI: " + t.getRequestURI());

    Headers headers = t.getResponseHeaders();
    headers.add("Content-Type", this.contentType);
    t.sendResponseHeaders(200, this.data.length);
    t.getResponseBody().write(this.data);
    t.getResponseBody().close();

  }

  private StaticResource(StaticResourceBuilder builder) {
    System.out.println("Loading file: " + builder.resourceUri);

    // Get the input stream for the text file
    try (InputStream inputStream = getClass().getResourceAsStream(builder.resourceUri)) {
      // Read all bytes from the input stream
      this.data = inputStream.readAllBytes();
    } catch (IOException e) {
      e.printStackTrace();
    }
    this.contentType = builder.contentType;
    this.isDebug = builder.isDebug;
  }

  public static StaticResourceBuilder builder(String resourceUri) {
    return new StaticResourceBuilder(resourceUri);
  }

  static class StaticResourceBuilder {
    private String resourceUri;
    private String contentType;
    private boolean isDebug = false;

    public StaticResourceBuilder(String resourceUri) {
      this.resourceUri = resourceUri;
    }

    public StaticResourceBuilder setContentType(String contentType) {
      this.contentType = contentType;
      return this;
    }

    public StaticResourceBuilder setDebug(boolean isDebug) {
      this.isDebug = isDebug;
      return this;
    }

    public StaticResource build() {
      if (this.contentType == null) {
        switch (FilenameUtils.getExtension(this.resourceUri)) {
          case "js":
            this.contentType = "application/javascript";
            break;
          case "css":
            this.contentType = "text/css";
            break;
          case "html":
            this.contentType = "text/html";
            break;
          default:
            this.contentType = "text/plain";
        }
      }
      return new StaticResource(this);
    }
  }
}

package com.example;
/*
*****************************************************************************
* Class: WebServePi
*
* Purpose:
* A multi-platform Java 17 Web Chat Server that is integrated with Raspberry Pi 4 B when ran from 64 bit OS.
* When ran on Pi, provides a 3D soft-button in HTML, CSS, and Javascript with near real-time JSON messaging which controls
* and listens to the primary circuit hard-button.
*
* Open-Source Credits and other references:
* https://pi4j.com/
* http://wiringpi.com/
* https://docs.oracle.com/en/java/javase/11/docs/api/jdk.httpserver/com/sun/net/httpserver/package-use.html#com.sun.net.httpserver
* https://github.com/FasterXML/jackson
* https://javadoc.io/static/com.corundumstudio.socketio/netty-socketio/1.7.19/com/corundumstudio/socketio/SocketIOServer.html
*
*
*****************************************************************************
* Note: All revisions must include this caveat and the below code header including
*       Initial History up to and including Version 1.0:
*****************************************************************************
*  Initial History :
*
*      DATE          by whom                   for what
*     --------------+----------------------+---------------------------------
*     03/16/2023     Chris C. Lukin         Initial implementation
*     06/05/2023     Chris C. Lukin         Initial cleanup and documentation
* © Copyright 2023   Chris C. Lukin
* Version 1.0
*****************************************************************************
*  Revisions:
*      DATE          by whom                   for what
*     --------------+----------------------+---------------------------------
*     07/11/2023     Kyle J. Bloom          Move runtime to resources
*     MM/DD/YYYY     MyName OrInitials      Next update history goes here
* Version X.X starts here
*
*****************************************************************************
*/

import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketConfig;
import com.corundumstudio.socketio.SocketIOServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pi4j.io.gpio.*;
import com.pi4j.io.gpio.event.GpioPinDigitalStateChangeEvent;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;
import com.pi4j.io.gpio.trigger.GpioBlinkStateTrigger;
import com.pi4j.io.gpio.trigger.GpioBlinkStopStateTrigger;
import com.pi4j.io.gpio.trigger.GpioCallbackTrigger;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;

public class WebServePi {

    private static final String RT_MESSAGE = "rt_message";
    private static final String RT_DISCONNECT = "rt_disconnect";
    private static final String RTPI_MESSAGE = "rtpi_message";
    private static final String RTPI_DISCONNECT = "rtpi_disconnect";
    private static final int PORT = 6180;
    private static final int SOCK_PORT = (PORT + 1);

    // private static final boolean bDEBUG = new Boolean(false);
    private static final boolean bDEBUG = FALSE;
    // private static final boolean bDEBUG = new Boolean(true);
    // private static final boolean bDEBUG = TRUE;

    private static HttpServer httpServer;

    private static SocketIOServer sioserver;

    private static WebServePi server = null;

    // Web Client files to serve
    private static byte[] indexhtml = null;
    private static byte[] sio = null;
    private static byte[] mytestjs = null;
    private static byte[] mystylescss = null;

    private static boolean bRaspBPi = false;

    // create gpio controller
    private static GpioPinDigitalOutput mySoftButton;

    public static void main(String[] args) throws Exception {
        System.out.println("Hello from WebServePi!");

        // BasicConfigurator.configure();
        System.out.println(System.getProperty("os.name"));

        // WebServePi server = new WebServePi();
        server = new WebServePi();

        bRaspBPi = DetectRaspBPi();
        System.out.println("Device RaspberryPi: " + bRaspBPi);

        if (bRaspBPi) {
            System.out.println("RaspberryPi Found");
            DoRasp();
        } else {
            System.out.println("RaspberryPi NOT Found");
        }

        server.start();
    }

    public void start() throws Exception {
        // Create HTTP server
        httpServer = HttpServer.create(new InetSocketAddress(PORT), 0);
        httpServer.createContext("/", new MyHandler());
        httpServer.createContext("/sio", new MyJSSocketIOClient());
        httpServer.createContext("/js", new MyJSHandler());
        httpServer.createContext("/css", new MyCSSHandler());
        // httpServer.setExecutor(Executors.newCachedThreadPool());
        httpServer.setExecutor(Executors.newFixedThreadPool(5));
        httpServer.start();
        System.out.println("HTTP Server started on port " + PORT);

        // Set SocketIO config
        Configuration config = new Configuration();
        // config.setHostname("localhost");
        config.setPort(SOCK_PORT);
        SocketConfig socketConfig = new SocketConfig();
        socketConfig.setReuseAddress(true);
        config.setSocketConfig(socketConfig);

        // Create SocketIO server
        // SocketIOServer server = new SocketIOServer(config);
        sioserver = new SocketIOServer(config);

        // Add Message handler for RT_MESSAGE
        sioserver.addEventListener(RT_MESSAGE, String.class, (client, data, ackSender) -> {
            // Callback for "rt_message" event
            var dstr_data = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")) + " -> " + data;
            System.out.println("Received message from client: " + dstr_data);
            sioserver.getBroadcastOperations().sendEvent(RT_MESSAGE, dstr_data);
        });

        // Add Message handler for RTPI_MESSAGE
        sioserver.addEventListener(RTPI_MESSAGE, String.class, (client, data, ackSender) -> {
            // Callback for "rtpi_message" event
            var dstr_data = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")) + " -> PI: " + data;
            System.out.println("Received client PI message from client: " + dstr_data);
            if (bDEBUG) {
                // do this for debugging
                sioserver.getBroadcastOperations().sendEvent(RTPI_MESSAGE, dstr_data);
            }

            // Process RTPI_MESSAGE for software button
            System.out.println("mySoftButton: " + mySoftButton.toString());
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(data);
            System.out.println(data);
            JsonNode pirequestNode = rootNode.path("pirequest");
            JsonNode button0 = pirequestNode.path("buttons").get(0);
            if (button0.get("request").textValue().contentEquals("PRESS")) {
                System.out.println("Button " + button0.get("number").asInt() + " pressed");
                mySoftButton.high();
            }
            if (button0.get("request").textValue().contentEquals("RELEASE")) {
                System.out.println("Button " + button0.get("number").asInt() + " released");
                mySoftButton.low();
            }

            // Possible memory leak; left this in place just in case
            objectMapper = null;

        });

        // Currently no client code for RT_DISCONNECT; more discovery design for this
        sioserver.addEventListener(RT_DISCONNECT, Void.class, (client, data, ackSender) -> {
            // Callback for "disconnect" event
            System.out.println("Client disconnected: " + client.getSessionId());
        });

        // Start the SocketIO server port
        // Possible future upgrade; web port to be upgraded to SocketIO port
        sioserver.start();
        System.out.println("SocketIOServer started on port " + SOCK_PORT);

        // Get Web Client files to serve
        GetWebClientFiles();
    }

    // Cache WebClient files
    private static void GetWebClientFiles() {

        String strindexfile = "index.html";
        if (bRaspBPi)
            strindexfile = "index_pi.html";

        indexhtml = ReadFileContentsAsBytesFromClassJar("/www/" + strindexfile).getBytes();
        sio = ReadFileContentsAsBytesFromClassJar("/www/js/socket.io.js").getBytes();
        mytestjs = ReadFileContentsAsBytesFromClassJar("/www/js/mytest.js").getBytes();
        mystylescss = ReadFileContentsAsBytesFromClassJar("/www/styles/mystyles.css").getBytes();
    }

    // Setup Raspberry Pi for inputs and outputs
    private static void DoRasp() {
        if (bRaspBPi) {
            System.out.println("Do Rasp");

            // Create gpio controller
            final GpioController gpio = GpioFactory.getInstance();

            // Create Hard button on pin #02 as an input pin with its internal pull down
            // resistor enabled
            final GpioPinDigitalInput myButton = gpio.provisionDigitalInputPin(RaspiPin.GPIO_02,
                    PinPullResistance.PULL_DOWN);

            // Create mySoftButton as output, which provides similar output as Hard button
            mySoftButton = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_05,
                    PinState.LOW);

            System.out.println("Creation mySoftButton: " + mySoftButton.toString());

            System.out.println(
                    "*-> Complete the GPIO #02,#04,#05 circuit and see the blink trigger with soft button control.");

            // Setup LED pin #04 as output pin, make sure they are all LOW at startup
            final GpioPinDigitalOutput myLed = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_04, PinState.LOW);

            // Hard Button trigger on the input pin ; when the input goes HIGH, turn on
            // blinking
            myButton.addTrigger(new GpioBlinkStateTrigger(PinState.HIGH, myLed, 250));

            // Button trigger on the input pin ; when the input goes LOW, turn off blinking
            myButton.addTrigger(new GpioBlinkStopStateTrigger(PinState.LOW, myLed));

            // Button Basic callback; software to decide event; currently no functionality
            // implemented
            myButton.addTrigger(new GpioCallbackTrigger(new Callable<Void>() {
                public Void call() {
                    System.out.println(" --> myButton GPIO TRIGGER CALLBACK RECEIVED ");
                    return null;
                }
            }));

            // Button callback listener for change of state
            myButton.addListener(new GpioPinListenerDigital() {
                @Override
                public void handleGpioPinDigitalStateChangeEvent(GpioPinDigitalStateChangeEvent event) {
                    // display pin state on console
                    // "GPIO 2" <GPIO 2> = LOW or HIGH
                    var dstr = LocalDateTime.now()
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
                    var strPinState = event.getPin().getPin() + " = " + event.getState();
                    var dstr_data = dstr + " -> " + strPinState;

                    System.out.println("[" + dstr +
                            "] --> GPIO PIN STATE CHANGE: " + strPinState);
                    sioserver.getBroadcastOperations().sendEvent("rt_message", dstr_data);
                }
            });

            // LED callback listener for change of state
            myLed.addListener(new GpioPinListenerDigital() {
                @Override
                public void handleGpioPinDigitalStateChangeEvent(GpioPinDigitalStateChangeEvent event) {
                    // display pin state on console
                    // "GPIO 4" = LOW or HIGH
                    var dstr = LocalDateTime.now()
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
                    var strPinState = event.getPin().getPin() + " = " + event.getState();
                    var dstr_data = dstr + " -> " + strPinState;

                    System.out.println("[" + dstr +
                            "] --> GPIO PIN STATE CHANGE: " + strPinState);

                    sioserver.getBroadcastOperations().sendEvent("rt_message", dstr_data);
                }
            });
        }
    }

    // Serve Entry Web page
    private static class MyHandler implements HttpHandler {
        public void handle(HttpExchange t) throws IOException {
            if (bDEBUG) {
                System.out.println("URI request: " + t.getRequestURI());
                System.out.println("Ends with / :" + t.getRequestURI().toString().endsWith("/"));
            }

            if (t.getRequestURI().toString().endsWith("/")) {
                // Serve index.html

                byte[] html = indexhtml;

                // Console output file
                // System.out.println("Serve JAR String index.html:\n" + new String(indexhtml));

                // System.out.println("html Index: \n" + new String(html));
                // System.out.println("html Index Length: " + html.length);

                Headers headers = t.getResponseHeaders();
                headers.add("Content-Type", "text/html");
                t.sendResponseHeaders(200, html.length);
                t.getResponseBody().write(html);
                t.getResponseBody().close();

            }
        }
    }

    // Serve Javascript
    private static class MyJSHandler implements HttpHandler {
        public void handle(HttpExchange t) throws IOException {
            if (bDEBUG)
                System.out.println("Request URI: " + t.getRequestURI());
            if (t.getRequestURI().toString().endsWith("mytest.js")) {

                byte[] js = mytestjs;

                Headers headers = t.getResponseHeaders();
                headers.add("Content-Type", "application/javascript");
                t.sendResponseHeaders(200, js.length);
                t.getResponseBody().write(js);
                t.getResponseBody().close();
            }
        }
    }

    // Serve Socket.io
    private static class MyJSSocketIOClient implements HttpHandler {
        public void handle(HttpExchange t) throws IOException {
            if (bDEBUG)
                System.out.println("Request URI: " + t.getRequestURI());
            if (t.getRequestURI().toString().endsWith("socket.io.js")) {

                byte[] js = sio;

                Headers headers = t.getResponseHeaders();
                headers.add("Content-Type", "application/javascript");
                t.sendResponseHeaders(200, js.length);
                t.getResponseBody().write(js);
                t.getResponseBody().close();
            }
        }
    }

    // Serve CSS
    private static class MyCSSHandler implements HttpHandler {
        public void handle(HttpExchange t) throws IOException {
            if (bDEBUG)
                System.out.println("Request URI: " + t.getRequestURI());
            if (t.getRequestURI().toString().endsWith("mystyles.css")) {

                byte[] css = mystylescss;

                Headers headers = t.getResponseHeaders();
                headers.add("Content-Type", "text/css");
                t.sendResponseHeaders(200, css.length);
                t.getResponseBody().write(css);
                t.getResponseBody().close();
            }
        }
    }

    // Use Helpers for info
    private static StringBuilder StringBuilderResult;
    private static ArrayList ListStringBuilderResult;

    static boolean DetectRaspBPi() {
        GetInfoByExecuteCommandLinux("cat /proc/device-tree/model", false);
        return StringBuilderResult.toString().toLowerCase().contains("raspberry");
    }

    // Helpers
    private static void GetInfoByExecuteCommandLinux(String command, boolean getList) {
        try {
            Process pb = new ProcessBuilder("bash", "-c", command).start();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(pb.getInputStream()));
            String line;
            if (getList) {
                ListStringBuilderResult = new ArrayList<>();
            } else {
                StringBuilderResult = new StringBuilder();

                while ((line = reader.readLine()) != null) {
                    if (getList) {
                        ListStringBuilderResult.add(line);
                    } else {
                        StringBuilderResult.append(line);
                    }
                }
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    private static String ReadFileContentsAsBytesFromClassJar(String filePath) {
        String fileContents = null;
        byte[] fileBytes = null;
        try {
            // Get the input stream for the text file
            // InputStream inputStream = this.getClass().getResourceAsStream(filePath);
            InputStream inputStream = server.getClass().getResourceAsStream(filePath);

            // Read all bytes from the input stream
            fileBytes = inputStream.readAllBytes();

            // Convert bytes to a string using UTF-8 encoding
            fileContents = new String(fileBytes, StandardCharsets.UTF_8);

            // Print the file contents
            // System.out.println(fileContents);

            // Close the input stream
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return fileContents;
    }
}

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
*     07/11/2023     Kyle J. Bloom          Refactor DetectRaspBPi
*     07/11/2023     Kyle J. Bloom          Extract HttpHandlers to StaticResource
*     07/11/2023     Kyle J. Bloom          Remove bRaspBPi global
*     MM/DD/YYYY     MyName OrInitials      Next update history goes here
* Version X.X starts here
*
*****************************************************************************
*/

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketConfig;
import com.corundumstudio.socketio.SocketIOServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalInput;
import com.pi4j.io.gpio.GpioPinDigitalOutput;
import com.pi4j.io.gpio.PinPullResistance;
import com.pi4j.io.gpio.PinState;
import com.pi4j.io.gpio.RaspiPin;
import com.pi4j.io.gpio.event.GpioPinDigitalStateChangeEvent;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;
import com.pi4j.io.gpio.trigger.GpioBlinkStateTrigger;
import com.pi4j.io.gpio.trigger.GpioBlinkStopStateTrigger;
import com.pi4j.io.gpio.trigger.GpioCallbackTrigger;
import com.sun.net.httpserver.HttpServer;

public class WebServePi {

    private static final String RT_MESSAGE = "rt_message";
    private static final String RT_DISCONNECT = "rt_disconnect";
    private static final String RTPI_MESSAGE = "rtpi_message";
    private static final String RTPI_DISCONNECT = "rtpi_disconnect";
    private static final int PORT = 6180;
    private static final int SOCK_PORT = (PORT + 1);

    private static final boolean bDEBUG = false;
    // private static final boolean bDEBUG = true;

    private static HttpServer httpServer;

    private static SocketIOServer sioServer;

    private static WebServePi server = null;

    // create gpio controller
    private static GpioPinDigitalOutput mySoftButton;

    public static void main(String[] args) throws Exception {
        System.out.println("Hello from WebServePi!");

        // BasicConfigurator.configure();
        System.out.println(System.getProperty("os.name"));

        // WebServePi server = new WebServePi();
        server = new WebServePi();

        boolean isRaspberryPi = DetectRaspBPi();
        System.out.println("Device RaspberryPi: " + isRaspberryPi);

        if (isRaspberryPi) {
            System.out.println("RaspberryPi Found");
            DoRasp();
        } else {
            System.out.println("RaspberryPi NOT Found");
        }

        server.start(isRaspberryPi);
    }

    public void start(boolean isRaspberryPi) throws Exception {
        String indexHtml = "/www/index.html";
        if (isRaspberryPi) {
            indexHtml = "/www/index_pi.html";
        }

        // Create HTTP server
        httpServer = HttpServer.create(new InetSocketAddress(PORT), 0);
        httpServer.createContext("/",
                StaticResource.builder(indexHtml)
                        .setDebug(bDEBUG)
                        .build());
        httpServer.createContext("/js/socket.io.js",
                StaticResource.builder("/www/js/socket.io.js")
                        .setDebug(bDEBUG)
                        .build());
        httpServer.createContext("/js/mytest.js",
                StaticResource.builder("/www/js/mytest.js")
                        .setDebug(bDEBUG)
                        .build());
        httpServer.createContext("/styles/mystyles.css",
                StaticResource.builder("/www/styles/mystyles.css")
                        .setDebug(bDEBUG)
                        .build());
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
        sioServer = new SocketIOServer(config);

        // Add Message handler for RT_MESSAGE
        sioServer.addEventListener(RT_MESSAGE, String.class, (client, data, ackSender) -> {
            // Callback for "rt_message" event
            var dstr_data = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")) + " -> " + data;
            System.out.println("Received message from client: " + dstr_data);
            sioServer.getBroadcastOperations().sendEvent(RT_MESSAGE, dstr_data);
        });

        // Add Message handler for RTPI_MESSAGE
        sioServer.addEventListener(RTPI_MESSAGE, String.class, (client, data, ackSender) -> {
            // Callback for "rtpi_message" event
            var dstr_data = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")) + " -> PI: " + data;
            System.out.println("Received client PI message from client: " + dstr_data);
            if (bDEBUG) {
                // do this for debugging
                sioServer.getBroadcastOperations().sendEvent(RTPI_MESSAGE, dstr_data);
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
        sioServer.addEventListener(RT_DISCONNECT, Void.class, (client, data, ackSender) -> {
            // Callback for "disconnect" event
            System.out.println("Client disconnected: " + client.getSessionId());
        });

        // Start the SocketIO server port
        // Possible future upgrade; web port to be upgraded to SocketIO port
        sioServer.start();
        System.out.println("SocketIOServer started on port " + SOCK_PORT);
    }

    // Setup Raspberry Pi for inputs and outputs
    private static void DoRasp() {
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
                sioServer.getBroadcastOperations().sendEvent("rt_message", dstr_data);
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

                sioServer.getBroadcastOperations().sendEvent("rt_message", dstr_data);
            }
        });
    }

    static boolean DetectRaspBPi() {
        try {
            String content = Files.readString(Paths.get("/proc/device-tree/model"));
            return content.toLowerCase().contains("raspberry");
        } catch (IOException e) {
            // Ignore
        }
        return false;
    }
}

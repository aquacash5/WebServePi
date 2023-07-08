# WebServePi
 Java Web Chat Server with soft-button functionality on Raspberry Pi 4B

Initial Copyright 2023 Chris Lukin

# Class: WebServePi

Purpose:
* A multi-platform Java 17 Web Chat Server that is integrated with Raspberry Pi 4 B when ran from 64 bit OS.
* When ran on Pi, provides a 3D soft-button in HTML, CSS, and Javascript with near real-time JSON messaging which controls
and listens to the primary circuit hard-button. When setup correctly, the soft/hard-button will blink the LED.

Open-Source Credits and other references:
* https://pi4j.com/
* http://wiringpi.com/
* https://docs.oracle.com/en/java/javase/11/docs/api/jdk.httpserver/com/sun/net/httpserver/package-use.html#com.sun.net.httpserver
* https://github.com/FasterXML/jackson
* https://javadoc.io/static/com.corundumstudio.socketio/netty-socketio/1.7.19/com/corundumstudio/socketio/SocketIOServer.html  
* https://github.com/Freenove/Freenove_Complete_Starter_Kit_for_Raspberry_Pi/blob/main/Tutorial.pdf

***
Install dependencies on Raspberry Pi 4 B, 64-bit OS:
1) Update the OS:  
sudo apt-get update   
sudo apt-get upgrade
2) Remove previous version of wiringpi. Retrieve, build, and install new wiringpi:  
sudo apt-get remove wiringpi -y  
sudo apt-get --yes install git-core gcc make   
cd ~  
git clone https://github.com/WiringPi/WiringPi --branch master --single-branch wiringpi  
cd ~/wiringpi  
sudo ./build  
3) The user must update to at least Java version: java 17.0.6  
   * Verify Java version:  
java --version

Build Circuit:
1) Reference "Project 2.1 Push Button Switch & LED"  
https://github.com/Freenove/Freenove_Complete_Starter_Kit_for_Raspberry_Pi/blob/main/Tutorial.pdf
2) The following changes:  
* wiringPi Pin 2, Hard Button - "pressed input pin" 
* wiringPi Pin 4, LED
* wiringPi Pin 5, additional Soft Button output pin to Hard Button "pressed input pin"

Execute after download:
* Download WebServePi-xx.xx.xx.zip
* Unzip the zip file
* Read the LICENSE and README
* Now Execute  
java WebServePi.jar

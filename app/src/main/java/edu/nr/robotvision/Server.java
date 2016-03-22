package edu.nr.robotvision;

import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

/**
 * Created by garrison on 21-3-16.
 */
public class Server implements Runnable {
    char delimiter = ':'; //The information is split distance:beta_h
    ServerSocket serverSocket;

    public Server() {
        try {
            serverSocket = new ServerSocket(1768);
        } catch (IOException e) {
            Log.e("Server", "Couldn't connect to port");
        } catch (SecurityException e) {
            Log.e("Server", "Server initialization was blocked");
        }
    }

    @Override
    public void run() {
        System.out.println("Started running");
        while(true) {
            Socket connectionSocket;
            try {
                connectionSocket = serverSocket.accept();
                BufferedReader inFromClient = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
                DataOutputStream outToClient = new DataOutputStream(connectionSocket.getOutputStream());

                inFromClient.readLine();

                String clientSentence = "" + MainActivity.distance + delimiter + MainActivity.turn;
                Log.d("Server", "About to send to client" + clientSentence);
                outToClient.writeBytes(clientSentence + '\n');
            } catch (IOException e) {
                e.printStackTrace();
            } catch (NullPointerException e) {
                try {
                    serverSocket = new ServerSocket(1768);
                } catch (IOException ex) {
                    Log.e("Server", "Couldn't connect to port");
                    long current = System.currentTimeMillis();
                    while(System.currentTimeMillis() < current + 1000) {}
                } catch (SecurityException ex) {
                    Log.e("Server", "Server initialization was blocked");
                }
            }
        }
    }
}

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

    double old_distance = MainActivity.distance;
    double old_turn = MainActivity.turn;
    long old_time = MainActivity.delta_time;

    public Server() {
        try {
            serverSocket = new ServerSocket(5432);
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
                while(true) {
                    DataOutputStream outToClient = new DataOutputStream(connectionSocket.getOutputStream());
                    if (old_distance != MainActivity.distance || old_turn != MainActivity.turn || old_time != MainActivity.delta_time) {
                        String clientSentence = "" + MainActivity.distance + delimiter + MainActivity.turn + ";" + MainActivity.delta_time;
                        old_distance = MainActivity.distance;
                        old_turn = MainActivity.turn;
                        old_time = MainActivity.delta_time;
                        outToClient.writeBytes(clientSentence + '\n');
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (Exception e) {
                try {
                    serverSocket = new ServerSocket(5432);
                } catch (IOException ex) {
                    Log.e("Server", "Couldn't connect to port");
                    try {
                        this.wait(1000);
                    }
                    catch (InterruptedException ei) {
                        ei.printStackTrace();
                    }
                } catch (SecurityException ex) {
                    Log.e("Server", "Server initialization was blocked");
                }
            }
        }
    }
}

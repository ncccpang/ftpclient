package tnl;

import java.io.*;
import java.net.*;



public class FTPClient {
    private String host;
    private int port;
    private Socket socket;

    private boolean hasLoggedIn = false;
    private String username;
    private String password;


    public FTPClient(String host, int port) throws Exception {
        this.host = host;
        this.port = port;

        socket = new Socket(host, port);
    }


}
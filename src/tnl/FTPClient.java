package tnl;

import java.io.*;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;

import com.sun.deploy.util.ArrayUtil;
import tnl.InvalidCommandException;



public class FTPClient {
    private class FTPResponse {
        public int code;
        public String message;



        public FTPResponse(String response) throws Exception {
            int firstSpaceIndex = response.indexOf(" ");

            if (firstSpaceIndex == -1) {
                throw new Exception("Invalid response");
            }

            String codeStr = response.substring(0, firstSpaceIndex);
            try {
                code = Integer.parseInt(codeStr);
            } catch (Exception e) {
                throw new Exception("Invalid response");
            }


            if (!FTPResponseCode.isValidCode(code)) {
                throw new Exception("Invalid response");
            }

            message = response.substring(firstSpaceIndex + 1);
        }

    }



    private static class FTPResponseCode {
        public static final int SIGNAL_DATA_CONNECTION_OPEN = 150;

        public static final int LOGGED_IN = 230;
        public static final int LOGGED_OUT = 221;
        public static final int ACTION_DONE = 250;
        public static final int DATA_TRANSFER_COMPLETED = 226;
        public static final int DATA_CONNECTION_OPEN_DONE = 200;

        public static final int  ENTER_PASS = 331;

        public static final int FORCED_LOGGED_OUT = 421;
        public static final int DATA_CONNECTION_OPEN_FAILED = 425;
        public static final int DATA_TRANSFER_ERROR = 426;

        public static final int SYNTAX_ERROR = 501;

        private static final List<Integer> RESPONSE_CODES = Arrays.asList(new Integer[] {
                150,
                230, 221, 250, 226, 200,
                331,
                421, 425, 426,
                501
        });



        public static boolean isValidCode(int code) {
            return RESPONSE_CODES.indexOf(code) != -1;
        }

    }



    private final Charset ENCODING_UTF8 = Charset.forName("UTF-8");



    private String host;
    private int port;

    private Socket socket;
    private BufferedReader inputStream;
    private PrintWriter outputStream;

    private boolean hasLoggedIn;
    private boolean userNameProvided;



    public FTPClient(String host, int port) throws Exception {
        this.host = host;
        this.port = port;

        socket = new Socket(host, port);

        inputStream = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        outputStream = new PrintWriter(socket.getOutputStream());

        hasLoggedIn = false;
        userNameProvided = false;
    }



    // TRUE = Command done with execution, FALSE = Command failed
    // Exception: I/O-related
    public boolean loginWithUsername(String username) throws Exception {
        if (hasLoggedIn) {
            throw new InvalidCommandException();
        }

        userNameProvided = true;

        String request = "USER " + username;

        outputStream.println(request);
        if (outputStream.checkError()) {
            throw new IOException();
        }

        String response = inputStream.readLine();

        FTPResponse ftpResponse = null;
        try {
            ftpResponse = new FTPResponse(response);
        } catch (Exception e) {
            // Invalid response, close the socket immediately
            close();
            return false;
        }

        // Logged in without password
        if (ftpResponse.code == FTPResponseCode.LOGGED_IN) {
            hasLoggedIn = true;
            return true;
        }

        // Password needed
        if (ftpResponse.code == FTPResponseCode.ENTER_PASS) {
            return true;
        }

        // Otherwise, regard as forced logged out, close the socket
        close();
        return false;
    }


    public boolean loginWithPassword(String password) throws Exception {
        if (hasLoggedIn || !userNameProvided) {
            throw new InvalidCommandException();
        }

        String request = "PASS " + password;

        outputStream.println(request);
        if (outputStream.checkError()) {
            throw new IOException();
        }

        String response = inputStream.readLine();

        FTPResponse ftpResponse = null;
        try {
            ftpResponse = new FTPResponse(response);
        } catch (Exception e) {
            // Invalid response, close the socket immediately
            close();
            return false;
        }

        // Login successfully
        if (ftpResponse.code == FTPResponseCode.LOGGED_IN) {
            hasLoggedIn = true;
            return true;
        }

        // Otherwise, regard as forced logged out, close the socket
        close();
        return false;
    }


    public boolean isLoggedIn() {
        return hasLoggedIn;
    }



    public void close() {
        try {
            outputStream.print("QUIT");

            inputStream.close();
            outputStream.close();

            socket.close();
        } catch (Exception e) {
            // Silently ignore the excception
        }

    }



    public void executeCommand(String command) {

    }

}
package tnl;

import java.io.*;
import java.net.Socket;
import java.nio.charset.Charset;

import com.sun.deploy.util.ArrayUtil;
import tnl.InvalidCommandException;



public class FTPClient {
    private class FTPResponse {
        public int code;
        public String message;



        public FTPResponse(String response) throws Exception {
            int firstSpaceIndex = response.indexOf(" ");

            if (firstSpaceIndex == -1) {
                throw new Exception("Invalid Response");
            }

            String codeStr = response.substring(0, firstSpaceIndex);

        }
    }



    private static class FTPResponseCode {
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

        private static String[] RESPONSE_CODES = {
                "230", "221", "250", "226", "200",
                "331",
                "421", "425", "426",
                "501"
        };

        public static boolean isValidCode(String code) {
//            return RESPONSE_CODES.
        }
    }

    public static enum LoginStatus {
        REQUIRE_PASSWORD,
        FORCED_LOGGED_OUT,
        LOGGED_IN
    }

    private final Charset ENCODING_UTF8 = Charset.forName("UTF-8");



    private String host;
    private int port;

    private Socket socket;
    private BufferedReader inputStream;
    private PrintWriter outputStream;

    private boolean hasLoggedIn = false;
    private String username;
    private String password;



    public FTPClient(String host, int port) throws Exception {
        this.host = host;
        this.port = port;

        socket = new Socket(host, port);

        inputStream = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        outputStream = new PrintWriter(socket.getOutputStream(), true);

        username = null;
        password = null;
    }



    public LoginStatus loginWithUsername(String username) throws Exception {
        if (hasLoggedIn) {
            throw new InvalidCommandException();
        }

        String request = "USER " + username;

        outputStream.println(request);
        if (outputStream.checkError()) {
            throw new IOException();
        }

        String response = inputStream.readLine();
    }



    public void close() {
        try {
            inputStream.close();
            outputStream.close();
            socket.close();
        } catch (Exception e) {
            // Silently ignore the excception
        }
    }
}
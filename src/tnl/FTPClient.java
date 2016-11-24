package tnl;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

import com.sun.deploy.util.ArrayUtil;
import com.sun.deploy.util.SyncFileAccess;
import tnl.InvalidCommandException;



public class FTPClient {
    private class FTPResponse {
        public int code;
        public String message;



        public FTPResponse(String response) throws Exception {
            int firstSpaceIndex = response.indexOf(" ");
            String codeStr;

            if (firstSpaceIndex == -1) {
                codeStr = response;
                message = "";
            } else {
                codeStr = response.substring(0, firstSpaceIndex);
                message = response.substring(firstSpaceIndex + 1);
            }

            try {
                code = Integer.parseInt(codeStr);
            } catch (Exception e) {
                throw new Exception("Invalid response");
            }

            if (!FTPResponseCode.isValidCode(code)) {
                throw new Exception("Invalid response");
            }

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
        public static final int REQUEST_FILE_ACTION_FAILED = 450;

        public static final int SYNTAX_ERROR = 501;

        private static final List<Integer> RESPONSE_CODES = Arrays.asList(new Integer[] {
                150,
                230, 221, 250, 226, 200,
                331,
                421, 425, 426, 450,
                501
        });



        public static boolean isValidCode(int code) {
            return RESPONSE_CODES.contains(code);
        }

    }



    private static class FTPRequestCode {
        public static final String USERNAME = "USER";
        public static final String PASSWORD = "PASS";

        public static final String OPEN_DATA_CONNECTION = "PORT";

        public static final String LIST_FILE_DIRECTORY = "LIST";
        public static final String GOTO_DIRECTORY = "CWD";

        public static final String DOWNLOAD_FILE = "RETR";
        public static final String UPLOAD_FILE_NO_OVERWITE = "STOU";
        public static final String UPLOAD_FILE_OVERWRITE = "STORE";

        public static final String LOGOUT = "QUIT";
    }



    private class ClientCommand {
        public String code;
        public ArrayList<String> arguments;

        public ClientCommand(String request) throws InvalidCommandException {
            arguments = new ArrayList<String>();

            int index = request.indexOf(" ");

            if (index == -1) {
                code = request;
                if (!CLIENT_COMMAND_CODES.contains(code)) {
                    throw new InvalidCommandException();
                }

                return;
            }

            code = request.substring(0, index).toLowerCase();
            if (!CLIENT_COMMAND_CODES.contains(code)) {
                throw new InvalidCommandException();
            }

            request = request.substring(index + 1).trim();

            index = 0;
            int len = request.length();
            int prev;

            while (index < len) {
                prev = index;

                if (request.charAt(index) == '\"') {
                    ++index;
                    while (index < len && request.charAt(index) != '\"') {
                        ++index;
                    }

                    if (index == len) {
                        throw new InvalidCommandException();
                    }

                    arguments.add(request.substring(prev + 1, index));

                    ++index;
                } else {
                    while (index < len && request.charAt(index) != ' ') {
                        ++index;
                    }

                    arguments.add(request.substring(prev, index));
                }

                while (index < len && request.charAt(index) == ' ') {
                    ++index;
                }

            }

        }

    }


    private static List<String> CLIENT_COMMAND_CODES = Arrays.asList(new String[] {
        "ls", "cd",
         "mkdir", "rm",
         "get", "put"
    });

    private final Charset ENCODING_UTF8 = Charset.forName("UTF-8");

    private final int BUFFER_SIZE = 1024;



    private Scanner scanConsole;

    private String host;
    private int port;
    private int dataPort;
    private Path clientDirectory;

    private Socket socket;
    private BufferedReader inputStream;
    private PrintWriter outputStream;

    private boolean hasLoggedIn;
    private boolean userNameProvided;



    public FTPClient(String host, int port, String clientDirectory) throws Exception {
        this.host = host;
        this.port = port;
        this.clientDirectory = Paths.get(clientDirectory);

        socket = new Socket(host, port);

        this.dataPort = socket.getLocalPort() + 1;

        inputStream = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        outputStream = new PrintWriter(socket.getOutputStream(), true);

        hasLoggedIn = false;
        userNameProvided = false;

        scanConsole = new Scanner(System.in);
    }



    // TRUE = Command done with execution, FALSE = Command failed
    // Exception: I/O-related
    public boolean loginWithUsername(String username) throws Exception {
        if (hasLoggedIn) {
            throw new InvalidCommandException();
        }

        userNameProvided = true;

        sendRequest(FTPRequestCode.USERNAME + " " + username);

        FTPResponse ftpResponse;
        try {
            ftpResponse = getResponse();
        } catch (Exception e) {
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

        sendRequest(FTPRequestCode.PASSWORD + " " + password);

        FTPResponse ftpResponse;
        try {
            ftpResponse = getResponse();
        } catch (Exception e) {
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
            outputStream.println("QUIT");

            inputStream.close();
            outputStream.close();

            socket.close();
        } catch (Exception e) {
            // Silently ignore the excception
        }

        hasLoggedIn = false;
    }



    public void executeCommand(String command)
            throws InvalidCommandException, AutoTerminatedException
    {
        ClientCommand clientCommand;

        try {
            clientCommand = new ClientCommand(command);
        } catch (InvalidCommandException e) {
            throw e;
        }

        if (clientCommand.code.equals("get")) {
            downloadFile(clientCommand.arguments);
        } else if (clientCommand.code.equals("put")) {

        }

    }

    private void sendRequest(String request) throws AutoTerminatedException {
        outputStream.println(request);

        if (outputStream.checkError()) {
            throw new AutoTerminatedException("Error sending request to server");
        }

    }

    private FTPResponse getResponse() throws AutoTerminatedException {
        String response;
        FTPResponse ftpResponse;

        try {
            response = inputStream.readLine();
        } catch (Exception e) {
            close();
            throw new AutoTerminatedException("Error reading response from server");
        }

        try {
            ftpResponse = new FTPResponse(response);
        } catch (Exception e) {
            close();
            throw new AutoTerminatedException("Invalid response from server");
        }

        return ftpResponse;
    }

    private Socket establishDataConnection() throws Exception {
        ServerSocket serverDataSocket = new ServerSocket(dataPort);
        serverDataSocket.setSoTimeout(4000);

        Socket dataSocket = serverDataSocket.accept();

        return dataSocket;
    }

    private void downloadFile(ArrayList<String> commandArugments)
            throws InvalidCommandException, AutoTerminatedException
    {
        if (commandArugments.size() != 1) {
            throw new InvalidCommandException();
        }

        File fileOut = clientDirectory.resolve(commandArugments.get(0)).toFile();

        // See if user wants to overwite already-existing ile.
        if (fileOut.exists()) {
            System.out.print("This file has already existed in your computer. Do you want to overwrite it (Y/N)? ");

            String overwrite = scanConsole.nextLine().trim().toLowerCase();

            if (overwrite.equals("n")) {
                return;
            }

        }

        FTPResponse ftpResponse;

        // Ask Server to open port
        sendRequest(
                FTPRequestCode.OPEN_DATA_CONNECTION + " "
                        + socket.getInetAddress().getHostAddress() + " "
                        + dataPort
        );

        ftpResponse = getResponse();

        if (ftpResponse.code == FTPResponseCode.FORCED_LOGGED_OUT) {
            close();
            throw new AutoTerminatedException("Server automatically logged out");
        }

        if (ftpResponse.code != FTPResponseCode.DATA_CONNECTION_OPEN_DONE) {
            close();
            throw new AutoTerminatedException("Invalid response from server");
        }

        // Send real File-Downloading request
        sendRequest(FTPRequestCode.DOWNLOAD_FILE + " " + commandArugments.get(0));

        ftpResponse = getResponse();

        if (ftpResponse.code == FTPResponseCode.FORCED_LOGGED_OUT) {
            close();
            throw new AutoTerminatedException("Server automatically logged out");
        }

        // File not exist on server. Terminated
        if (ftpResponse.code == FTPResponseCode.REQUEST_FILE_ACTION_FAILED) {
            System.out.println("This file does not exist on server!");
            return;
        }

        if (ftpResponse.code != FTPResponseCode.SIGNAL_DATA_CONNECTION_OPEN) {
            close();
            throw new AutoTerminatedException("Invalid response from server");
        }

        FileOutputStream fileOutStream;

        try {
            fileOutStream = new FileOutputStream(fileOut);
        } catch (Exception e) {
            System.out.println("Error creating file in your computer!");
            return;
        }

        Socket dataSocket = null;
        DataInputStream dataInputStream = null;
        byte[] buffer = new byte[BUFFER_SIZE];

        try {
            dataSocket = establishDataConnection();
            dataInputStream = new DataInputStream(dataSocket.getInputStream());
        } catch (Exception e) {
            try {
                // Close data socket
                dataInputStream.close();
                dataSocket.close();

                fileOutStream.close();
            } catch (Exception se) {
                // Silently ignore the exception
            }

            System.out.println("Error establishing data connection!");
            return;
        }

        int byteReceived;
        int errorOccured = 0;

        while (true) {
            try {
                byteReceived = dataInputStream.read(buffer, 0, BUFFER_SIZE);
            } catch (Exception e) {
                errorOccured = 1;
                break;
            }

            if (byteReceived == -1) {
                break;
            }

            try {
                fileOutStream.write(buffer, 0, byteReceived);
            } catch (Exception e) {
                errorOccured = 2;
                break;
            }

        }

        if (errorOccured == 0) {
            try {
                fileOutStream.flush();
            } catch (Exception e) {
                errorOccured = 2;
            }

        }

        try {
            fileOutStream.close();

            dataInputStream.close();
            dataSocket.close();
        } catch (Exception e) {
            // Silently ignore this error
        }

        if (errorOccured == 2) {
            System.out.println("Error saving downloaded file to computer!");
        } else if (errorOccured == 1) {
            System.out.println("Error retrieving file data from server!");
        }

        ftpResponse = getResponse();

        // File download succesfully, without any error
        if (ftpResponse.code == FTPResponseCode.DATA_TRANSFER_COMPLETED) {
            System.out.println(String.format("File '%s' has been downloaded successfully", commandArugments.get(0)));
            return;
        }

        // Otherwise, downloaded file has error and need deleting
        try {
            fileOut.delete();
        } catch (Exception e) {
            // Silently ignore the exception
        }

        if (ftpResponse.code == FTPResponseCode.DATA_CONNECTION_OPEN_FAILED) {
            System.out.println("Error retrieving file data from server!");
            return;
        } else if (ftpResponse.code == FTPResponseCode.FORCED_LOGGED_OUT) {
            close();
            throw new AutoTerminatedException("Server automatically logged out");
        } else {
            throw new AutoTerminatedException("Invalid response from server");
        }

    }

}
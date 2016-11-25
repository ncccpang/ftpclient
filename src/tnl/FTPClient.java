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
        public static final int REQUEST_ACTION_DONE = 250;
        public static final int DATA_TRANSFER_COMPLETED = 226;
        public static final int DATA_CONNECTION_OPEN_DONE = 200;

        public static final int ENTER_PASS = 331;

        public static final int FORCED_LOGGED_OUT = 421;
        public static final int DATA_CONNECTION_OPEN_FAILED = 425;
        public static final int DATA_TRANSFER_ERROR = 426;
        public static final int REQUEST_FILE_ACTION_FAILED = 450;
        public static final int REQUEST_ACTION_FAILED = 451;

        public static final int SYNTAX_ERROR = 501;

        private static final List<Integer> RESPONSE_CODES = Arrays.asList(new Integer[]{
                150,
                230, 221, 250, 226, 200,
                331,
                421, 425, 426, 450, 451,
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

        public static final String MAKE_NEW_DIRECTORY = "MKD";
        public static final String LIST_FILE_DIRECTORY = "LIST";
        public static final String GOTO_DIRECTORY = "CWD";

        public static final String DELETE = "DELE";

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


    private static List<String> CLIENT_COMMAND_CODES = Arrays.asList(new String[]{
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


    public FTPClient(String host, int port, int dataPort, String clientDirectory) throws Exception {
        this.host = host;
        this.port = port;
        this.clientDirectory = Paths.get(clientDirectory);

        socket = new Socket(host, port);

        this.dataPort = dataPort;

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
            sendRequest(FTPRequestCode.LOGOUT);

            inputStream.close();
            outputStream.close();

            socket.close();
        } catch (Exception e) {
            // Silently ignore the excception
        }

        hasLoggedIn = false;
    }


    public void executeCommand(String command)
            throws InvalidCommandException, AutoTerminatedException {
        ClientCommand clientCommand;

        try {
            clientCommand = new ClientCommand(command);
        } catch (InvalidCommandException e) {
            throw e;
        }

        if (clientCommand.code.equals("get")) {
            downloadFile(clientCommand.arguments);

        } else if (clientCommand.code.equals("put")) {
            uploadFile(clientCommand.arguments);

        } else if (clientCommand.code.equals("rm")) {
            deletePath(clientCommand.arguments);

        } else if (clientCommand.code.equals("mkdir")) {
            createNewDirectory(clientCommand.arguments);

        } else if (clientCommand.code.equals("cd")) {
            changeCurrentDirectoryOnServer(clientCommand.arguments);

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

        // Wait long enough to prevent immature timeout
        serverDataSocket.setSoTimeout(20000);

        Socket dataSocket = serverDataSocket.accept();

        // Close the serverSocket. This will not affect the socket created through accept()
        try {
            serverDataSocket.close();
        } catch (Exception e) {
            // Silently ignore the exception
        }

        return dataSocket;
    }

    private void downloadFile(ArrayList<String> commandArguments)
            throws InvalidCommandException, AutoTerminatedException {
        if (commandArguments.size() < 1 || commandArguments.size() > 2) {
            throw new InvalidCommandException();
        }

        String fileNameOnServer = commandArguments.get(0);

        String fileNameOnLocal = fileNameOnServer;
        if (commandArguments.size() == 2) {
            fileNameOnLocal = commandArguments.get(1);
        }

        File fileRetrieved = clientDirectory.resolve(fileNameOnLocal).toFile();

        // See if user wants to overwite already-existing ile.
        if (fileRetrieved.exists()) {
            System.out.print(String.format(
                    "File '%s' is already existed in your computer. Do you want to overwrite it (Y/N)? ",
                    fileNameOnLocal
            ));

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
        sendRequest(FTPRequestCode.DOWNLOAD_FILE + " " + fileNameOnServer);

        ftpResponse = getResponse();

        if (ftpResponse.code == FTPResponseCode.FORCED_LOGGED_OUT) {
            close();
            throw new AutoTerminatedException("Server automatically logged out");
        }

        // File not exist on server. Terminated
        if (ftpResponse.code == FTPResponseCode.REQUEST_FILE_ACTION_FAILED) {
            System.out.println(String.format(
                    "File '%s' does not exist on server!",
                    fileNameOnServer
            ));

            return;
        }

        if (ftpResponse.code != FTPResponseCode.SIGNAL_DATA_CONNECTION_OPEN) {
            close();
            throw new AutoTerminatedException("Invalid response from server");
        }

        FileOutputStream fileRetrievedOutStream;

        try {
            fileRetrievedOutStream = new FileOutputStream(fileRetrieved);
        } catch (Exception e) {
            System.out.println("Error creating file in your computer!");
            return;
        }

        Socket dataSocket = null;
        DataInputStream dataSocketInpStream = null;
        byte[] buffer = new byte[BUFFER_SIZE];

        try {
            dataSocket = establishDataConnection();
            dataSocketInpStream = new DataInputStream(dataSocket.getInputStream());
        } catch (Exception e) {
            try {
                // Close data socket
                dataSocketInpStream.close();
                dataSocket.close();

                fileRetrievedOutStream.close();
            } catch (Exception se) {
                // Silently ignore the exception
            }

            e.printStackTrace();

            System.out.println("Error establishing data connection!");
            return;
        }

        int byteReceived;
        int errorOccured = 0;

        while (true) {
            try {
                byteReceived = dataSocketInpStream.read(buffer, 0, BUFFER_SIZE);
            } catch (Exception e) {
                errorOccured = 2;
                break;
            }

            if (byteReceived == -1) {
                break;
            }

            try {
                fileRetrievedOutStream.write(buffer, 0, byteReceived);
                fileRetrievedOutStream.flush();
            } catch (Exception e) {
                errorOccured = 1;
                break;
            }

        }

        try {
            fileRetrievedOutStream.close();

            dataSocketInpStream.close();
            dataSocket.close();
        } catch (Exception e) {
            // Silently ignore this error
        }

        if (errorOccured == 1) {
            System.out.println("Error saving downloaded file to computer!");
        } else if (errorOccured == 2) {
            System.out.println("Error retrieving file data from server!");
        }

        ftpResponse = getResponse();

        // File download succesfully, without any error
        if (ftpResponse.code == FTPResponseCode.DATA_TRANSFER_COMPLETED && errorOccured == 0) {
            System.out.println(String.format("File '%s' has been downloaded successfully", commandArguments.get(0)));
            return;
        }

        // Otherwise, downloaded file has error and need deleting
        try {
            fileRetrieved.delete();
        } catch (Exception e) {
            // Silently ignore the exception
        }

        if (ftpResponse.code == FTPResponseCode.DATA_TRANSFER_ERROR) {
            System.out.println("Error retrieving file data from server!");
            return;
        } else if (ftpResponse.code == FTPResponseCode.FORCED_LOGGED_OUT) {
            close();
            throw new AutoTerminatedException("Server automatically logged out");
        } else {
            close();
            throw new AutoTerminatedException("Invalid response from server");
        }

    }

    private void uploadFile(ArrayList<String> commandArguments)
            throws InvalidCommandException, AutoTerminatedException {
        if (commandArguments.size() < 1 || commandArguments.size() > 2) {
            throw new InvalidCommandException();
        }

        String fileNameOnLocal = commandArguments.get(0);

        String fileNameOnServer = fileNameOnLocal;
        if (commandArguments.size() == 2) {
            fileNameOnServer = commandArguments.get(1);
        }

        File fileUploaded = clientDirectory.resolve(fileNameOnLocal).toFile();

        // See if the uploaded file exists or not
        if (!fileUploaded.exists()) {
            System.out.print(String.format(
                    "File '%s' does not exist in your client directory!",
                    fileNameOnLocal
            ));

            return;
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

        // Try sending upload-no-overwrite request first
        sendRequest(FTPRequestCode.UPLOAD_FILE_NO_OVERWITE + " " + fileNameOnServer);

        ftpResponse = getResponse();

        if (ftpResponse.code == FTPResponseCode.FORCED_LOGGED_OUT) {
            close();
            throw new AutoTerminatedException("Server automatically logged out");
        }

        // File already exist on server. Ask user if they want to overwrite it or not
        if (ftpResponse.code != FTPResponseCode.SIGNAL_DATA_CONNECTION_OPEN) {
            if (ftpResponse.code != FTPResponseCode.REQUEST_FILE_ACTION_FAILED) {
                close();
                throw new AutoTerminatedException("Invalid response from server");
            }

            String overwrite;

            System.out.print(String.format(
                    "File '%s' already exists on the server. Do you want to overwrite it (Y/N)? ",
                    fileNameOnServer
            ));

            overwrite = scanConsole.nextLine().trim().toUpperCase();

            if (overwrite.equals("N")) {
                return;
            }

            // If user wants to overwrite the file on the server, then send upload-overwrite request
            sendRequest(FTPRequestCode.UPLOAD_FILE_OVERWRITE + " " + fileNameOnServer);

            ftpResponse = getResponse();

            if (ftpResponse.code == FTPResponseCode.FORCED_LOGGED_OUT) {
                close();
                throw new AutoTerminatedException("Server automatically logged out");
            }

            if (ftpResponse.code != FTPResponseCode.SIGNAL_DATA_CONNECTION_OPEN) {
                close();
                throw new AutoTerminatedException("Invalid response from server");
            }

        }

        FileInputStream fileUploadedInStream;

        try {
            fileUploadedInStream = new FileInputStream(fileUploaded);
        } catch (Exception e) {
            System.out.println("Error accessing file in your computer!");
            return;
        }

        Socket dataSocket = null;
        DataOutputStream dataSocketOutStream = null;
        byte[] buffer = new byte[BUFFER_SIZE];

        try {
            dataSocket = establishDataConnection();
            dataSocketOutStream = new DataOutputStream(dataSocket.getOutputStream());
        } catch (Exception e) {
            try {
                // Close data socket
                dataSocketOutStream.close();
                dataSocket.close();

                fileUploadedInStream.close();
            } catch (Exception se) {
                // Silently ignore the exception
            }

            e.printStackTrace();

            System.out.println("Error establishing data connection!");
            return;
        }

        int byteSent;
        int errorOccured = 0;

        while (true) {
            try {
                byteSent = fileUploadedInStream.read(buffer, 0, BUFFER_SIZE);
            } catch (Exception e) {
                errorOccured = 1;
                break;
            }

            if (byteSent == -1) {
                break;
            }

            try {
                dataSocketOutStream.write(buffer, 0, byteSent);
                dataSocketOutStream.flush();
            } catch (Exception e) {
                errorOccured = 2;
                break;
            }

        }

        try {
            fileUploadedInStream.close();

            dataSocketOutStream.close();
            dataSocket.close();
        } catch (Exception e) {
            // Silently ignore this error
        }

        if (errorOccured == 2) {
            System.out.println("Error uploading file to server!");
        } else if (errorOccured == 1) {
            System.out.println("Error accessing uploaded file data in computer!");
        }

        ftpResponse = getResponse();

        // File uploaded succesfully, without any error
        if (ftpResponse.code == FTPResponseCode.DATA_TRANSFER_COMPLETED && errorOccured == 0) {
            System.out.println(String.format("File '%s' has been uploaded successfully", fileNameOnLocal));
            return;
        }

        // Otherwise, error occurs
        if (ftpResponse.code == FTPResponseCode.DATA_CONNECTION_OPEN_FAILED) {
            System.out.println("Error uploading file data from server!");
        } else if (ftpResponse.code == FTPResponseCode.FORCED_LOGGED_OUT) {
            close();
            throw new AutoTerminatedException("Server automatically logged out");
        }

        if (ftpResponse.code != FTPResponseCode.DATA_TRANSFER_COMPLETED) {
            close();
            throw new AutoTerminatedException("Invalid response from server");
        } else {
            // Error occurs during file transmission period, but server regards it as correct transmission
            // the file uploaded to server is corrupted.
            // Therefore, we should issue a request to delete it

            sendRequest(FTPRequestCode.DELETE + " " + fileNameOnServer);

            ftpResponse = getResponse();

            if (ftpResponse.code == FTPResponseCode.FORCED_LOGGED_OUT) {
                close();
                throw new AutoTerminatedException("Server automatically logged out");
            }

        }

    }

    private void deletePath(ArrayList<String> commandArguments)
            throws InvalidCommandException, AutoTerminatedException {
        if (commandArguments.size() != 1) {
            throw new InvalidCommandException();
        }

        sendRequest(FTPRequestCode.DELETE + " " + commandArguments.get(0));

        FTPResponse ftpResponse = getResponse();

        if (ftpResponse.code == FTPResponseCode.FORCED_LOGGED_OUT) {
            close();
            throw new AutoTerminatedException("Server automatically logged out");
        }

        if (ftpResponse.code == FTPResponseCode.REQUEST_ACTION_DONE) {
            return;
        }

        if (ftpResponse.code == FTPResponseCode.REQUEST_ACTION_FAILED) {
            System.out.println(ftpResponse.message + "!");
            return;
        }

        // Invalid response
        close();
        throw new AutoTerminatedException("Invalid response from server");
    }

    private void createNewDirectory(ArrayList<String> commandArguments)
            throws InvalidCommandException, AutoTerminatedException {
        if (commandArguments.size() != 1) {
            throw new InvalidCommandException();
        }

        sendRequest(FTPRequestCode.MAKE_NEW_DIRECTORY + " " + commandArguments.get(0));

        FTPResponse ftpResponse = getResponse();

        if (ftpResponse.code == FTPResponseCode.FORCED_LOGGED_OUT) {
            close();
            throw new AutoTerminatedException("Server automatically logged out");
        }

        if (ftpResponse.code == FTPResponseCode.REQUEST_ACTION_DONE) {
            return;
        }

        if (ftpResponse.code == FTPResponseCode.REQUEST_ACTION_FAILED) {
            System.out.println(ftpResponse.message + "!");
            return;
        }

        // Invalid response
        close();
        throw new AutoTerminatedException("Invalid response from server");

    }

    private void changeCurrentDirectoryOnServer(ArrayList<String> commandArguments)
            throws InvalidCommandException, AutoTerminatedException
    {
        if (commandArguments.size() > 1) {
            throw new InvalidCommandException();
        }

        if (commandArguments.size() == 0) {
            sendRequest(FTPRequestCode.GOTO_DIRECTORY);
        } else {
            sendRequest(FTPRequestCode.GOTO_DIRECTORY + " " + commandArguments.get(0));
        }

        FTPResponse ftpResponse = getResponse();

        if (ftpResponse.code == FTPResponseCode.FORCED_LOGGED_OUT) {
            close();
            throw new AutoTerminatedException("Server automatically logged out");
        }

        if (ftpResponse.code == FTPResponseCode.REQUEST_ACTION_DONE) {
            return;
        }

        if (ftpResponse.code == FTPResponseCode.REQUEST_ACTION_FAILED) {
            System.out.println(ftpResponse.message + "!");
            return;
        }

        // Invalid response
        close();
        throw new AutoTerminatedException("Invalid response from server");

    }


}
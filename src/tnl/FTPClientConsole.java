/*
Name: TA Ngoc Linh
ID: 20213201
Email: nlta@connect.ust.hk
 */

package tnl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;
import tnl.FTPClient;



public class FTPClientConsole {
    private static final String DEFAULT_HOST_ADDRESS = "localhost";
    private static final int DEFAULT_HOST_PORT = 6788;



    private static Scanner scanConsole = new Scanner(System.in);
    private static FTPClient ftpClient;



    public static void main(String[] argv) {
        String host;
        int port, dataPort;
        String clientDirectory;

        System.out.println("FTP Client");
        System.out.println("----------");
        System.out.println("");

        // Input Host, Port, Client directory
        System.out.print("Host IP/domain name (leave blank for default: " + DEFAULT_HOST_ADDRESS + "): ");
        host = scanConsole.nextLine().trim();

        if (host.equals("")) {
            host = DEFAULT_HOST_ADDRESS;
        }

        System.out.print("Host port number: (leave blank for default: " + DEFAULT_HOST_PORT + "): ");
        String textInput = scanConsole.nextLine().trim();

        if (textInput.equals("")) {
            port = DEFAULT_HOST_PORT;
        } else {
            try {
                port = Integer.parseInt(textInput);
            } catch (Exception e) {
                System.out.println("\nInvalid port number! Terminated.");

                return;
            }

        }

        System.out.print("Port number for data transmission (Maximum port is 65535. Make sure your inputted port is free): ");
        textInput = scanConsole.nextLine().trim();

        try {
            dataPort = Integer.parseInt(textInput);

            if (dataPort > 65535) {
                throw new Exception();
            }

        } catch (Exception e) {
            System.out.println("\nInvalid port number! Terminated.");

            return;
        }

        System.out.print("Absolute path to Client base directory: ");
        clientDirectory = scanConsole.nextLine();

        try {
            if (!Paths.get(clientDirectory).toRealPath().toFile().isDirectory()) {
                System.out.println("Invalid path! Terminated");

                return;
            }

        } catch (Exception e) {
            System.out.println("Invalid path! Terminated");

            return;
        }

        // Try connecting to server
        try {
            ftpClient = new FTPClient(host, port, dataPort, clientDirectory);
        } catch (Exception e) {
            System.out.println(String.format("Cannot connect to %s:%d! Terminated.", host, port));

            return;
        }

        System.out.println();

        // Username
        String username;
        boolean success = false;

        System.out.print("Username: ");
        username = scanConsole.nextLine();

        try {
            success = ftpClient.loginWithUsername(username);
        } catch (Exception e) {
            System.out.println("Error sending username to server! Terminated.");

            return;
        }

        if (!success) {
            System.out.println("Invalid username! Terminated.");

            return;
        }

        // It has been logged in already
        if (ftpClient.isLoggedIn()) {
            startMainSession();
            return;
        }

        // Otherwise, password is required
        String password;

        System.out.print("Password: ");
        password = scanConsole.nextLine();

        try {
            success = ftpClient.loginWithPassword(password);
        } catch (Exception e) {
            System.out.println("Error sending password to server! Terminated.");

            return;
        }

        if (!success || !ftpClient.isLoggedIn()) {
            System.out.println("Invalid password! Terminated.");

            return;
        }

        startMainSession();
    }



    private static void startMainSession() {
        System.out.println("\nLogin is successful!\n");
        System.out.println("Type help/h/H for help");
        System.out.println("Type exit to exit the program");
        System.out.println();

        String command;

        while (ftpClient.isLoggedIn()) {
            System.out.print(String.format("%s> ", ftpClient.getStatusHeader()));
            command = scanConsole.nextLine().trim();

            if (command.equals("")) {
                continue;
            }

            if (command.equals("h") || command.equals("help") || command.equals("H")) {
                System.out.println("ls                                          List the files/directories in server");
                System.out.println("cd                                          Navigate to the base directory in server");
                System.out.println("cd  <path>                                  Navigate to a directory in server");
                System.out.println("cd ..                                       Navigate to the parent directory of the current directory in server");
                System.out.println("mkdir <dir_name>                            Create a new directory");
                System.out.println("rm <path>                                   Remove a file or an empty directory");
                System.out.println("get <file_name> (<file_name_on_local>)      Download a file to client (optionally, under a new name)");
                System.out.println("put <file_name> (<file_name_on_server>)     Upload a file to server (optionally,  under a new name)");
                System.out.println("help                                        Get help");
                System.out.println("exit                                        Exit the program");

                continue;
            }

            if (command.equals("exit")) {
                System.out.print("Closing connection");

                ftpClient.close();
                return;
            }

            try {
                ftpClient.executeCommand(command);
            } catch (InvalidCommandException e) {
                System.out.println("Invalid command!");
            } catch (AutoTerminatedException e) {
                System.out.println(String.format("%s! Connection automatically terminated", e.getMessage()));

                return;
            }

        }
    }

}

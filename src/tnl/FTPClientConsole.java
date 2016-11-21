package tnl;

import java.util.Scanner;
import tnl.FTPClient;



public class FTPClientConsole {
    private static Scanner scanConsole = new Scanner(System.in);
    private static FTPClient ftpClient;



    public static void main(String[] argv) {
        String host;
        int port;

        System.out.println("FTP Client");
        System.out.println("----------");
        System.out.println("");

        // Input Host & Port
        System.out.print("Host IP/domain name: ");
        host = scanConsole.nextLine();

        System.out.print("Host port number: ");

        try {
            port = scanConsole.nextInt();

            // Consume the '\n' character in order to prepare for the next nextLine()
            scanConsole.nextLine();
        } catch (Exception e) {
            System.out.println("\nInvalid port number! Terminated.");

            return;
        }

        // Try connecting to server
        try {
            ftpClient = new FTPClient(host, port);
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

            ftpClient.close();
            return;
        }

        if (!success) {
            System.out.println("Invalid username! Terminated.");

            ftpClient.close();
            return;
        }

        // It has been logged in already
        if (ftpClient.isLoggedIn()) {
            System.out.println("\nLogin is successful!\n");

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

            ftpClient.close();
            return;
        }

        if (success && ftpClient.isLoggedIn()) {
            System.out.println("\nLogin is successful!\n");
        } else {
            System.out.println("Invalid username! Terminated.");

            ftpClient.close();
            return;
        }

        startMainSession();
    }



    private static void startMainSession() {
        String command;

        while (ftpClient.isLoggedIn()) {
            System.out.print("> ");
            command = scanConsole.nextLine().trim();

            if (command.equals("")) {
                continue;
            }

            ftpClient.executeCommand(command);
        }
    }

}

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
        } catch (Exception e) {
            System.out.println("\nInvalid port number! Terminated.");
            return;
        }

        // Try connecting to server
        try {
            ftpClient = new FTPClient(host, port);
        } catch (Exception e) {
            System.out.println(String.format("Cannot connect to %s:%d! Terminated", host, port));
        }

    }
}

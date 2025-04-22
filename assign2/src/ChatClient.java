import java.io.*;
import java.net.*;
import java.util.Scanner;

public class ChatClient {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java ChatClient <server_address> <port>");
            return;
        }
        String serverAddress = args[0];
        int port = Integer.parseInt(args[1]);
        try (Socket socket = new Socket(serverAddress, port);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             Scanner scanner = new Scanner(System.in)) {

            System.out.println(in.readLine());
            System.out.print("Enter your credentials: ");
            String credentials = scanner.nextLine();
            out.println(credentials);

            System.out.println(in.readLine());

            Thread readThread = new Thread(() -> {
                String response;
                try {
                    while ((response = in.readLine()) != null) {
                        System.out.println(response);
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            });
            readThread.start();

            while (true) {
                String msg = scanner.nextLine();
                if (msg.equalsIgnoreCase("exit"))
                    break;
                out.println(msg);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
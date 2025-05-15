import java.io.*;
import java.net.*;
import java.util.Scanner;
import java.time.Duration;

public class ChatClient {
    private static final String TOKEN_FILE = "token.txt";
    private static final Duration TOKEN_TTL = Duration.ofMinutes(1);

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

            String savedToken = readToken();
            if (savedToken != null) {
                System.out.println("Reconnecting with saved token...");
                out.println(savedToken);
            } else {
                System.out.print("Enter your credentials: ");
                out.println(scanner.nextLine());
            }

            String authResp = in.readLine();
            System.out.println(authResp);
            if (savedToken == null && authResp.startsWith("Authentication successful")) {
                String tok = authResp.substring(authResp.indexOf(':') + 1).trim();
                saveToken(tok);
                System.out.println("Token saved for next reconnect.");
            }

            Thread reader = new Thread(() -> {
                try {
                    String resp;
                    while ((resp = in.readLine()) != null) {
                        System.out.println(resp);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            reader.setDaemon(true);
            reader.start();

            while (true) {
                String msg = scanner.nextLine();
                out.println(msg);
                if (msg.equalsIgnoreCase("exit")) {
                    deleteToken();
                    System.out.println("Local token deleted. Bye!");
                    break;
                }
            }

        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private static String readToken() {
        File f = new File(TOKEN_FILE);
        if (!f.exists()) return null;
        // expira após TOKEN_TTL
        if (System.currentTimeMillis() - f.lastModified() > TOKEN_TTL.toMillis()) {
            f.delete();
            return null;
        }
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            return r.readLine().trim();
        } catch (IOException e) {
            return null;
        }
    }

    private static void saveToken(String token) {
        try (PrintWriter w = new PrintWriter(new FileWriter(TOKEN_FILE))) {
            w.println(token);
        } catch (IOException e) {
            // ignore
        }
    }

    private static void deleteToken() {
        new File(TOKEN_FILE).delete();
    }
}
import java.io.*;
import java.net.*;
import java.time.Duration;
import java.util.Scanner;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class ChatClient {
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String PURPLE = "\u001B[35m";
    private static final String CYAN = "\u001B[36m";
    
    private static final String TOKEN_FILE = System.getProperty("user.dir") + "/token.txt";
    private static final Duration TOKEN_TTL = Duration.ofMinutes(15);

    public static void main(String[] args) throws IOException {

        
        if (args.length < 2) {
            System.out.println(YELLOW + "Usage: java ChatClient <server_address> <port>" + RESET);
            return;
        }
        String serverAddress = args[0];
        int port = Integer.parseInt(args[1]);

        try (Socket socket = new Socket(serverAddress, port);
             BufferedReader in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter    out = new PrintWriter(socket.getOutputStream(), true);
             Scanner scanner    = new Scanner(System.in)) {

            String welcome = in.readLine();
            if (welcome == null) return;
            System.out.println(welcome);

            String prompt = in.readLine();
            if (prompt == null) return;
            System.out.println(prompt);

            String token = readToken();
            if (token != null) {
                System.out.println("Reconnecting automatically...");
                out.println(token);
            } else {
                String choice = scanner.nextLine().trim();
                out.println(choice);
            }

            String line;
            while ((line = in.readLine()) != null) {
                System.out.println(line);

                if (line.endsWith(":")) {
                    out.println(scanner.nextLine());
                    continue;
                }
                if (line.startsWith("Reconnected")) {
                    break;
                }
                if (line.startsWith("Authentication successful")) {
                    String newToken = line.substring(line.indexOf(':')+1).trim();
                    saveToken(newToken);
                    break;
                }
                if (line.contains("failed")) {
                    System.out.println("Exiting.");
                    return;
                }
            }

                       Thread reader = new Thread(() -> {
                try {
                    String msg;
                    while ((msg = in.readLine()) != null) {
                        System.out.println(msg);
                    }
                } catch (IOException ignored) { }
            });
            reader.setDaemon(true);
            reader.start();

            while (true) {
                System.out.print(BOLD + "> " + RESET);
                String msg = scanner.nextLine();
                if (msg.equalsIgnoreCase("exit")) {
                    deleteToken();
                    System.out.println(GREEN + "Bye!" + RESET);
                    out.println("exit");
                    break;
                }
                out.println(msg);
            }
        }
    }

    private static String readToken() {
        File f = new File(TOKEN_FILE);
        if (!f.exists()) return null;
        long age = System.currentTimeMillis() - f.lastModified();
        if (age > TOKEN_TTL.toMillis()) {
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
        } catch (IOException ignored) { }
    }

    private static void deleteToken() {
        new File(TOKEN_FILE).delete();
    }
}

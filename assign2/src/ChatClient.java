import java.io.*;
import java.net.*;
import java.time.Duration;
import java.util.Scanner;

public class ChatClient {
    private static final String TOKEN_FILE = "token.txt";
    private static final Duration TOKEN_TTL = Duration.ofMinutes(15);

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("Usage: java ChatClient <server_address> <port>");
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
                    System.out.println("Token saved; automatic reconnect until it expires.");
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
                } catch (IOException e) {
                    System.out.println("Ligação perdida. A tentar reconectar...");

                    PrintWriter[] outRef = new PrintWriter[1];
                    BufferedReader[] inRef = new BufferedReader[1];
                    String newToken = readToken();
                    Socket newSocket = reconnectToServer(serverAddress, port, newToken, outRef, inRef);

                    if (newSocket == null) {
                        System.out.println("Falha ao reconectar. Por favor reinicia o cliente.");
                        return;
                    }

                    PrintWriter newOut = outRef[0];
                    BufferedReader newIn = inRef[0];

                    // Nova thread para receber mensagens
                    Thread newReader = new Thread(() -> {
                        try {
                            String msg2;
                            while ((msg2 = newIn.readLine()) != null) {
                                System.out.println(msg2);
                            }
                        } catch (IOException ex) {
                            System.out.println("Reconexão falhou novamente.");
                        }
                    });
                    newReader.setDaemon(true);
                    newReader.start();

                    Scanner scanner2 = new Scanner(System.in);
                    while (true) {
                        String msg2 = scanner2.nextLine();
                        if (msg2.equalsIgnoreCase("exit")) {
                            deleteToken();
                            System.out.println("Local token deleted. Bye!");
                            newOut.println("exit");
                            break;
                        }
                        newOut.println(msg2);
                    }
                }
            });
            reader.setDaemon(true);
            reader.start();

            while (true) {
                String msg = scanner.nextLine();
                if (msg.equalsIgnoreCase("exit")) {
                    deleteToken();
                    System.out.println("Local token deleted. Bye!");
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

    private static Socket reconnectToServer(String serverAddress, int port, String token, PrintWriter[] outRef, BufferedReader[] inRef) {
        int attempts = 0;
        while (attempts < 5) {
            try {
                Socket socket = new Socket(serverAddress, port);
                outRef[0] = new PrintWriter(socket.getOutputStream(), true);
                inRef[0] = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                outRef[0].println(token); // reenviar token
                return socket;
            } catch (IOException e) {
                System.out.println("Reconnecting... attempt " + (attempts + 1));
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                attempts++;
            }
        }
        return null;
    }

}
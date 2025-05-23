import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.locks.*;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.time.Instant;
import java.time.Duration;

public class ChatServer {
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String PURPLE = "\u001B[35m";
    private static final String CYAN = "\u001B[36m";

    private static final List<String> LLM_COMMAND = getLLMCommand();

    private static final Duration SESSION_TTL = Duration.ofMinutes(15);
    private static final File USER_FILE = new File("users.txt");

    private final Map<String, UserSession> sessions = new HashMap<>();
    private final Lock sessionsLock = new ReentrantLock();

    private final Map<String, ChatRoom> rooms = new HashMap<>();
    private final Lock roomsLock = new ReentrantLock();

    private final Map<String, String> userCredentials = new HashMap<>();
    private final Lock usersLock = new ReentrantLock();


    private static List<String> getLLMCommand() {
        String cmd = System.getenv("OLLAMA_CMD");
        if (cmd != null && !cmd.isBlank()) {
            return Arrays.asList(cmd.trim().split("\\s+"));
        }
        return List.of("ollama", "run", "llama2");
    }


    public ChatServer() {
        loadUserFile();
        Thread.startVirtualThread(() -> {
            try {
                while (true) {
                    Thread.sleep(Duration.ofMinutes(1).toMillis());
                    purgeExpiredSessions();
                }
            } catch (InterruptedException ignored) { }
        });
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java ChatServer <port>");
            return;
        }
        int port = Integer.parseInt(args[0]);
        try {
            new ChatServer().start(port);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to start server on port " + port);
        }
    }

    public void start(int port) throws Exception {
        SSLServerSocket serverSocket = createSSLServerSocket(port);
        System.out.println("Secure Chat server started on port " + port);

        while (true) {
            SSLSocket clientSocket = (SSLSocket) serverSocket.accept();
            Thread.startVirtualThread(() -> handleClient(clientSocket));
        }
    }

    private SSLServerSocket createSSLServerSocket(int port) throws Exception {
        SSLServerSocketFactory factory = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
        SSLServerSocket serverSocket = (SSLServerSocket) factory.createServerSocket(port);

        return serverSocket;
    }

    private void loadUserFile() {
        try {
            if (!USER_FILE.exists()) USER_FILE.createNewFile();
            try (BufferedReader r = new BufferedReader(new FileReader(USER_FILE))) {
                String line;
                while ((line = r.readLine()) != null) {
                    String[] p = line.split(":", 2);
                    if (p.length == 2) userCredentials.put(p[0], p[1]);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveUser(String user, String pass) {
        usersLock.lock();
        try (PrintWriter w = new PrintWriter(new FileWriter(USER_FILE, true))) {
            userCredentials.put(user, pass);
            w.println(user + ":" + pass);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            usersLock.unlock();
        }
        loadUserFile();
    }

    private boolean checkCredentials(String user, String pass) {
        usersLock.lock();
        try {
            return pass.equals(userCredentials.get(user));
        } finally {
            usersLock.unlock();
        }
    }

    private void handleClient(Socket clientSocket) {
        try (
            clientSocket;
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
        ) {
            out.println(boxText("CHAT SERVER", '=', 50));
            out.println(BOLD + CYAN + "WELCOME TO THE CHAT SERVER!" + RESET);
            out.println(YELLOW + "Please select an option:" + RESET);
            out.println(BOLD + "1" + RESET + " - Login");
            out.println(BOLD + "2" + RESET + " - Register");
            out.println("-------------------------------------------");

            String firstLine = in.readLine();
            if (firstLine == null) return;

            UserSession session = null;
            sessionsLock.lock();
            
            try {
                UserSession s = sessions.get(firstLine.trim());
                if (s != null
                    && Duration.between(s.lastAccess, Instant.now()).compareTo(SESSION_TTL) <= 0) {
                    s.touch();
                    session = s;
                } else {
                    sessions.remove(firstLine.trim());
                }
            } finally {
                sessionsLock.unlock();
            }

            String username, password;
            if (session != null) {
                out.println("Reconnected in room: " + session.roomName);
                joinRoom(session.roomName, session.username, out);
                return;
            } else {
                String option = firstLine.trim();
                if (!option.equals("1") && !option.equals("2")) {
                    out.println("1-Login  2-Register:");
                    option = in.readLine();
                    if (option == null) return;
                }

                if (option.equals("2")) {
                    while (true) {
                        out.println("Choose username:");
                        username = in.readLine();
                        if (username == null) return;
                        username = username.trim();
                        usersLock.lock();
                        try {
                            if (userCredentials.containsKey(username)) {
                                out.println("Username already exists. Please choose another.");
                                continue; 
                            }
                        } finally {
                            usersLock.unlock();
                        }
                        
                        out.println("Choose password:");
                        password = in.readLine();
                        if (password == null) return;
                        password = password.trim();
                        
                        saveUser(username, password);
                        out.println("Registration successful.");
                        break;
                    }
                    
                    out.println("Login");
                }


                out.println("Username:");
                username = in.readLine();
                out.println("Password:");
                password = in.readLine();
                if (username == null || password == null
                    || !checkCredentials(username.trim(), password.trim())) {
                    out.println("Authentication failed.");
                    return;
                }

                String token = UUID.randomUUID().toString();
                session = new UserSession(token, username.trim());
                sessionsLock.lock();
                try { sessions.put(token, session); }
                finally    { sessionsLock.unlock(); }
                out.println("Authentication successful!");

                out.println(boxText("AVAILABLE ROOMS", '-', 40));
                out.println(CYAN + "Rooms: " + BOLD + getRoomList() + RESET);
                out.println(YELLOW + "Enter room name to join or create:" + RESET);
                String roomName = in.readLine();
                if (roomName == null || roomName.isBlank()) {
                    out.println("Invalid room. Disconnecting.");
                    return;
                }
                session.roomName = roomName.trim();
                joinRoom(session.roomName, session.username, out);
            }

            String message;
            while ((message = in.readLine()) != null) {
                if (message.isBlank()) continue;

                if (message.equalsIgnoreCase("/rooms")) {
                    out.println(boxText("AVAILABLE ROOMS", '-', 40));
                    out.println(CYAN + "Rooms: " + BOLD + getRoomList() + RESET);
                }
                else if (message.equalsIgnoreCase("/help")) {
                    showHelpMenu(out);
                }
                else if (message.toLowerCase().startsWith("/join ")) {
                    String newRoom = message.substring(6).trim();
                    if (newRoom.isEmpty()) {
                        out.println("Usage: /join <roomName>");
                    } else {
                        ChatRoom old = rooms.get(session.roomName);
                        if (old != null) {
                            old.broadcast(">> " + session.username + " has left " + session.roomName);
                            old.removeClient(session.username);
                        }
                        session.roomName = newRoom;
                        joinRoom(newRoom, session.username, out);
                    }
                }
                else if (message.equalsIgnoreCase("/leave")) {
                    ChatRoom curr = rooms.get(session.roomName);
                    if (curr != null) {
                        curr.broadcast(">> " + session.username + " has left " + session.roomName);
                        curr.removeClient(session.username);
                        session.roomName = null;
                        out.println("You have left the room. Use /join <room> to enter another.");
                    } else {
                        out.println("You are not in any room.");
                    }
                }
                else if (message.equalsIgnoreCase("exit")) {
                    ChatRoom room = rooms.get(session.roomName);
                    if (room != null) {
                        room.broadcast(">> " + session.username + " has disconnected.");
                        room.removeClient(session.username);
                    }
                    break;
                }
                else {
                    ChatRoom room = rooms.get(session.roomName);
                    if (room != null) {
                        String userLine = session.username + ": " + message;
                        room.broadcast(userLine);

                        if (room.isAiRoom) {
                            String aiResponse = callLLM(message, room.history, room.prompt);
                            room.broadcast("Bot: " + aiResponse);
                        }
                    } else {
                        out.println("You are not in a room. Use /join <room> first.");
                    }
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private boolean authenticate(String credentials) {
        return credentials != null && !credentials.isEmpty();
    }

    private List<String> getRoomList() {
        roomsLock.lock();
        try {
            return new ArrayList<>(rooms.keySet());
        } finally {
            roomsLock.unlock();
        }
    }

    private ChatRoom joinRoom(String roomName, String clientId, PrintWriter out) {
        roomsLock.lock();
        try {
            ChatRoom room = rooms.get(roomName);
            boolean isAi = roomName.startsWith("AI ");
            String prompt = isAi ? roomName.substring(3).trim() : null;

            if (room == null) {
                room = new ChatRoom(roomName, isAi, prompt);
                rooms.put(roomName, room);
            }
            room.addClient(new ClientHandler(clientId, out));
            if (roomName!= "exit") room.broadcast(">> " + clientId + " joined the room '" + roomName + "'.");
            return room;
        } finally {
            roomsLock.unlock();
        }
    }


    private String callLLM(String userMessage, List<String> history, String prompt) {
        try {
            ProcessBuilder builder = new ProcessBuilder(LLM_COMMAND);
            builder.redirectErrorStream(true);
            Process process = builder.start();

            try (
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))
            ) {
                for (String line : history) {
                    String msg = line.substring(line.indexOf(':') + 1).trim();
                    if (line.startsWith("Bot:")) {
                        writer.write("Bot: " + msg + "\n");
                    } else {
                        writer.write("User: " + msg + "\n");
                    }
                }

                writer.write("User: " + userMessage + "\n");
                writer.flush();
                process.getOutputStream().close();

                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line).append("\n");
                }

                String resp = response.toString().trim();

                resp = resp.replaceAll("^```+", "")
                        .replaceAll("```+$", "")
                        .trim();
                return resp;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return "[Error talking to AI]";
        }
    }

    private void purgeExpiredSessions() {
        sessionsLock.lock();
        try {
            Instant now = Instant.now();
            Iterator<Map.Entry<String, UserSession>> it = sessions.entrySet().iterator();
            while (it.hasNext()) {
                if (Duration.between(it.next().getValue().lastAccess, now).compareTo(SESSION_TTL) > 0) {
                    it.remove();
                }
            }
        } finally {
            sessionsLock.unlock();
        }
    }

    private static class UserSession {
        final String token;
        final String username;
        String roomName;
        Instant lastAccess;

        UserSession(String token, String user) {
            this.token = token;
            this.username = user;
            this.lastAccess = Instant.now();
        }

        void touch() {
            lastAccess = Instant.now();
        }
    }

    private static class ChatRoom {
        final String name;
        final boolean isAiRoom;
        final String prompt;
        final List<String> history = new ArrayList<>();
        final Lock clientsLock = new ReentrantLock();
        final List<ClientHandler> clients = new ArrayList<>();

        ChatRoom(String name, boolean isAiRoom, String prompt) {
            this.name = name;
            this.isAiRoom = isAiRoom;
            this.prompt = prompt;
        }

        void addClient(ClientHandler c) {
            clientsLock.lock();
            try {
                clients.add(c);
            } finally {
                clientsLock.unlock();
            }
        }

        void removeClient(String id) {
            clientsLock.lock();
            try {
                clients.removeIf(c -> c.clientId.equals(id));
            } finally {
                clientsLock.unlock();
            }
        }

        void broadcast(String msg) {
            String formattedMsg = msg;
            if (msg.startsWith(">>")) {
                formattedMsg = BLUE + msg + RESET;
            } else if (msg.startsWith("Bot:")) {
                formattedMsg = PURPLE + msg + RESET;
            }
            
            synchronized (history) {
                history.add(msg);
            }
            clientsLock.lock();
            try {
                for (ClientHandler c : clients) {
                    c.send(formattedMsg);
                }
            } finally {
                clientsLock.unlock();
            }
        }
    }

    private static class ClientHandler {
        private final PrintWriter out;
        private final String clientId;
        private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();

        ClientHandler(String clientId, PrintWriter out) {
            this.clientId = clientId;
            this.out = out;
            Thread.startVirtualThread(() -> {
                try {
                    while (true) {
                        String msg = queue.take();
                        out.println(msg);
                    }
                } catch (InterruptedException ignored) { }
            });
        }

        void send(String message) {
            queue.offer(message);
        }
    }

    private String boxText(String text, char borderChar, int width) {
        StringBuilder box = new StringBuilder();
        String border = String.valueOf(borderChar).repeat(width);
        
        box.append(BOLD + PURPLE + border + RESET + "\n");
        box.append(BOLD + BLUE + " " + text + " " + RESET + "\n");
        box.append(BOLD + PURPLE + border + RESET);
        
        return box.toString();
    }
    
    private void showHelpMenu(PrintWriter out) {
        out.println(boxText("HELP MENU", '-', 50));
        out.println(BOLD + "Available Commands:" + RESET);
        out.println(YELLOW + "/rooms" + RESET + " - Show available rooms");
        out.println(YELLOW + "/join <roomname>" + RESET + " - Join or create a room");
        out.println(YELLOW + "/leave" + RESET + " - Leave the current room");
        out.println(YELLOW + "/help" + RESET + " - Show this help menu");
        out.println(YELLOW + "exit" + RESET + " - Disconnect from server");
        out.println(BOLD + "In AI rooms:" + RESET);
        out.println("Type any message to interact with the AI assistant.");
        out.println("Create an AI room with name starting with 'AI '");
    }
}

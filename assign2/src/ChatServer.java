import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.locks.*;
import java.util.UUID;
import java.util.stream.Collectors;

public class ChatServer {

    private final Map<String, UserSession> sessions = new HashMap<>();
    private final Lock sessionsLock = new ReentrantLock();

    private final Map<String, ChatRoom> rooms = new HashMap<>();
    private final Lock roomsLock = new ReentrantLock();

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java ChatServer <port>");
            return;
        }
        int port = Integer.parseInt(args[0]);
        new ChatServer().start(port);
    }

    public void start(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Chat server started on port " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                Thread.ofVirtual().start(() -> handleClient(clientSocket));
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void handleClient(Socket clientSocket) {
        try (
            clientSocket;
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
        ) {
            out.println("Welcome! Type your username to authenticate:");
            String line = in.readLine();
            if (line == null) return;

            UserSession session = null;
            sessionsLock.lock();
            try {
                session = sessions.get(line.trim());
            } finally {
                sessionsLock.unlock();
            }
            if (session != null) {
                out.println("Reconnected as " + session.username
                            + " in room: " + session.roomName);
                roomsLock.lock();
                try {
                    ChatRoom room = rooms.get(session.roomName);
                    if (room != null) {
                        room.removeClient(session.username);
                        room.addClient(new ClientHandler(session.username, out));
                        room.broadcast(">> " + session.username + " reconnected.");
                    }
                } finally {
                    roomsLock.unlock();
                }
            } else {
                String username = line.trim();
                if (!authenticate(username)) {
                    out.println("Authentication failed.");
                    return;
                }
                String token = UUID.randomUUID().toString();
                session = new UserSession(token, username);

                sessionsLock.lock();
                try {
                    sessions.put(token, session);
                } finally {
                    sessionsLock.unlock();
                }

                out.println("Authentication successful. Your token: " + token);
                out.println("Available rooms: " + getRoomList());
                out.println("Enter the room name to join or create:");
                String roomName = in.readLine();
                if (roomName == null || roomName.isBlank()) {
                    out.println("Invalid room name. Disconnecting.");
                    return;
                }
                session.roomName = roomName;
                joinRoom(roomName, session.username, out);
            }

            String message;
            while ((message = in.readLine()) != null) {
                if (message.isBlank()) continue;

                if (message.equalsIgnoreCase("/rooms")) {
                    out.println("Available rooms: " + getRoomList());
                }
                else if (message.toLowerCase().startsWith("/join ")) {
                    String newRoom = message.substring(6).trim();
                    if (newRoom.isEmpty()) {
                        out.println("Usage: /join <roomName>");
                    } else {
                        // leave room
                        ChatRoom old = rooms.get(session.roomName);
                        if (old != null) {
                            old.broadcast(">> " + session.username + " has left " + session.roomName);
                            old.removeClient(session.username);
                        }
                        // join new
                        session.roomName = newRoom;
                        joinRoom(newRoom, session.username, out);
                    }
                }
                else if (message.toLowerCase().startsWith("/leave")) {
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
                        room.broadcast(session.username + ": " + message);
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
            String prompt = isAi
                ? roomName.substring(3).trim()
                : null;

            if (room == null) {
                room = new ChatRoom(roomName, isAi, prompt);
                rooms.put(roomName, room);
            }
            room.addClient(new ClientHandler(clientId, out));
            room.broadcast(">> " + clientId + " joined the room ' " + roomName + " '.");
            return room;
        } finally {
            roomsLock.unlock();
        }
    }

    private static class UserSession {
        final String token;
        final String username;
        String roomName;

        UserSession(String t, String u) {
            token = t;
            username = u;
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
            // guarda no histórico
            synchronized(history) {
                history.add(msg);
            }
            // envia a todos
            clientsLock.lock();
            try {
                for (ClientHandler c : clients) c.send(msg);
            } finally {
                clientsLock.unlock();
            }
            // se for AI room e mensagem de user, gera resposta
            if (isAiRoom && !msg.startsWith("Bot:")) {
                String aiReply = callAiModel();
                broadcast("Bot: " + aiReply);
            }
        }

        private String callAiModel() {
            StringBuilder sb = new StringBuilder();
            if (prompt != null) sb.append(prompt).append("\n");
            synchronized(history) {
                for (String m : history) sb.append(m).append("\n");
            }
            try {
                ProcessBuilder pb = new ProcessBuilder(
                    "ollama","run","<seu-modelo>","--prompt", sb.toString()
                );
                pb.redirectErrorStream(true);
                Process p = pb.start();
                try (BufferedReader r = new BufferedReader(
                         new InputStreamReader(p.getInputStream()))) {
                    return r.lines().collect(Collectors.joining(" "));
                }
            } catch (IOException e) {
                return "[erro ao chamar LLM]";
            }
        }
    }

    private static class ClientHandler {
        private final PrintWriter out;
        private final String clientId;

        ClientHandler(String clientId, PrintWriter out) {
            this.clientId = clientId;
            this.out = out;
        }

        void send(String message) {
            out.println(message);
        }
    }
}
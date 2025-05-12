import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.locks.*;
import java.util.UUID;

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
            out.println("Welcome! Send your token to reconnect, or type your username to authenticate:");
            String line = in.readLine();
            if (line == null) return;

            UserSession session = null;
            sessionsLock.lock();
            try {
                session = sessions.get(line.trim());
            } finally {
                sessionsLock.unlock();
            }

            // Reconnect path
            if (session != null) {
                out.println("Reconnected as " + session.username);
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
                // First‐time auth
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

            // Message loop
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
                        // leave old room
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
                    // fully disconnect
                    ChatRoom room = rooms.get(session.roomName);
                    if (room != null) {
                        room.broadcast(">> " + session.username + " has disconnected.");
                        room.removeClient(session.username);
                    }
                    break;
                }
                else {
                    // normal chat
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
            if (room == null) {
                room = new ChatRoom(roomName);
                rooms.put(roomName, room);
            }
            room.addClient(new ClientHandler(clientId, out));
            room.broadcast(">> " + clientId + " joined the room.");
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
        private final String name;
        private final List<ClientHandler> clients = new ArrayList<>();
        private final Lock clientsLock = new ReentrantLock();

        ChatRoom(String name) {
            this.name = name;
        }

        void addClient(ClientHandler client) {
            clientsLock.lock();
            try {
                clients.add(client);
            } finally {
                clientsLock.unlock();
            }
        }

        void removeClient(String clientId) {
            clientsLock.lock();
            try {
                clients.removeIf(c -> c.clientId.equals(clientId));
            } finally {
                clientsLock.unlock();
            }
        }

        void broadcast(String message) {
            clientsLock.lock();
            try {
                for (ClientHandler c : clients) c.send(message);
            } finally {
                clientsLock.unlock();
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
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.locks.*;

public class ChatServer {

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
                Thread.startVirtualThread(() -> handleClient(clientSocket));
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
            out.println("Welcome to the Chat System! Please authenticate.");
            String credentials = in.readLine();
            if (!authenticate(credentials)) {
                out.println("Authentication failed.");
                return;
            }
            out.println("Authentication successful. Available rooms: " + getRoomList());
            
            out.println("Enter the room name to join or create:");
            String roomName = in.readLine();
            if (roomName == null || roomName.isBlank()){
                out.println("Invalid room name. Disconnecting.");
                return;
            }
            String clientId = credentials.trim();
            ChatRoom room = joinRoom(roomName, clientId, out);

            String message;
            while ((message = in.readLine()) != null) {
                if (message.equalsIgnoreCase("exit")) {
                    room.broadcast(">> " + clientId + " has left the room.");
                    break;
                }
                String formattedMessage = clientId + ": " + message;
                room.broadcast(formattedMessage);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
    
    private boolean authenticate(String credentials) {
        // Simple authentication stub: accept if non-empty.
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
            // Add client to the room.
            room.addClient(new ClientHandler(clientId, out));
            room.broadcast(">> " + clientId + " joined the room.");
            return room;
        } finally {
            roomsLock.unlock();
        }
    }
    
    private void processMessage(String message, PrintWriter out) {
        System.out.println("Received: " + message);
        out.println("Message received: " + message);
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
        
        void broadcast(String message) {
            clientsLock.lock();
            try {
                for (ClientHandler client : clients) {
                    client.send(message);
                }
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

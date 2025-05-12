import java.net.*;
import java.io.*;
import java.util.*;


class Client {
    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length < 4) {
            System.out.println("Use: java Client <addr> <port> <op> <id> [<val>]");
            return;
        }

        String serverAddress = args[0];
        int port = Integer.parseInt(args[1]);
        String operation = args[2];
        int sensorId = Integer.parseInt(args[3]);

        DatagramSocket socket = new DatagramSocket();
        InetAddress serverIP = InetAddress.getByName(serverAddress);

        if (operation.equals("put") && args.length == 5) {
            float value = Float.parseFloat(args[4]);
            String message = "put " + sensorId + " " + value;
            sendUDPMessage(socket, serverIP, port, message);
        } else if (operation.equals("get")) {
            String message = "get " + sensorId;
            sendUDPMessage(socket, serverIP, port, message);
            receiveResponse(socket);
        } else {
            System.out.println("Invalid operation.");
        }
        socket.close();
    }

    private static void sendUDPMessage(DatagramSocket socket, InetAddress serverIP, int port, String message) throws IOException {
        byte[] data = message.getBytes();
        DatagramPacket packet = new DatagramPacket(data, data.length, serverIP, port);
        socket.send(packet);
    }

    private static void receiveResponse(DatagramSocket socket) throws IOException {
        byte[] buffer = new byte[1024];
        DatagramPacket response = new DatagramPacket(buffer, buffer.length);
        socket.receive(response);
        String receivedMessage = new String(response.getData(), 0, response.getLength());
        System.out.println(receivedMessage);
    }
}

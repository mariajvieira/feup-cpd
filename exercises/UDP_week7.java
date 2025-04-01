import java.net.*;
import java.io.*;
import java.util.*;

public class Server {

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("Usage: java Server <port> <no_sensors>");
            return;
        }   

        int port = Integer.parseInt(args[0]);
        DatagramSocket socket = new DatagramSocket(port);
        System.out.println("Server is listening on port " + port);

        byte[] buffer = new byte[BUFFER_SIZE];
        while (true) {
            DatagramPacket request = new DatagramPacket(buffer, buffer.length);
            socket.receive(request);
            String message = new String(request.getData(), 0, request.getLength());

            String response = processMessage(message);
            if (response != null) {
                byte[] responseData = response.getBytes();
                DatagramPacket responsePacket = new DatagramPacket(responseData, responseData.length,
                        request.getAddress(), request.getPort());
                socket.send(responsePacket);
            }
        }
    }

    private static processMessage(String message) {
        String[] parts = message.split(" ");
        if (parts.length < 2) return null;

        String operation = parts[0];
        int sensorId = Integer.parseInt(parts[1]);
    
        if (operation.equals("put") && parts.length == 3) {
            float value = Float.parseFloat(parts[2]);
            sensorData.computeIfAbsent(sensorId, k -> new ArrayList<>()).add(value);
            return null;
        } else if (operation.equals("get")) {
            List<Float> values = sensorData.get(sensorId);
            if (values == null || values.isEmpty()) return "Sensor " + sensorId + " not found";
            float average = (float) values.stream().maptoDouble(Float::doubleValue).average().orElse(0.0);
            return "Sensor " + sensorId + " average: " + average;
        }
        return "Invalid command";
    }
}



public class Client {

    
}
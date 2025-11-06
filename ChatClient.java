
import java.io.*;
import java.net.*;

public class ChatClient {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: java ChatClient <server-ip> <port>");
            return;
        }
        String serverIp = args[0];
        int port = Integer.parseInt(args[1]);

        try (Socket socket = new Socket(serverIp, port);
             BufferedReader serverIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedReader userIn = new BufferedReader(new InputStreamReader(System.in));
             PrintWriter serverOut = new PrintWriter(socket.getOutputStream(), true)) {

            // Thread to print server messages
            new Thread(() -> {
                try {
                    String s;
                    while ((s = serverIn.readLine()) != null) System.out.println(s);
                } catch (IOException e) { /* closed */ }
            }).start();

            // Read user input and send to server
            String input;
            while ((input = userIn.readLine()) != null) {
                serverOut.println(input);
                if (input.equalsIgnoreCase("/quit")) break;
            }
        }
    }
}

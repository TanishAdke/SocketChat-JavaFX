import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ChatServer {
    private static final int PORT = 12345;
    private static final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();

    public static void main(String[] args) throws Exception {
        System.out.println("Server starting on port " + PORT);
        try (ServerSocket server = new ServerSocket(PORT)) {
            while (true) {
                Socket sock = server.accept();
                ClientHandler handler = new ClientHandler(sock);
                clients.add(handler);
                new Thread(handler).start();
            }
        }
    }

    static void broadcast(String msg, ClientHandler sender) {
        for (ClientHandler c : clients) {
            if (c != sender) c.send(msg);
        }
    }

    static void remove(ClientHandler c) { clients.remove(c); }

    static class ClientHandler implements Runnable {
        private Socket s;
        private PrintWriter out;
        private String name = "User";

        ClientHandler(Socket socket) {
            this.s = socket;
        }

        public void run() {
            try (
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()))
            ) {
                out = new PrintWriter(s.getOutputStream(), true);
                out.println("Welcome! Type your name:");
                name = in.readLine();
                broadcast(name + " joined.", this);
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.equalsIgnoreCase("/quit")) break;
                    broadcast(name + ": " + line, this);
                }
            } catch (IOException e) {
                System.err.println("Client error: " + e.getMessage());
            } finally {
                try { s.close(); } catch (IOException ignored) {}
                remove(this);
                broadcast(name + " left.", this);
            }
        }

        void send(String msg) {
            if (out != null) out.println(msg);
        }
    }
}

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import javafx.geometry.Insets;


public class ChatServerGUI extends Application {
    private TextArea logArea;
    private Button startBtn;
    private TextField portField;
    private ServerSocket serverSocket;
    private final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();
    private ExecutorService pool = Executors.newCachedThreadPool();
    private volatile boolean running = false;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(true);

        startBtn = new Button("Start Server");
        portField = new TextField("12345");
        portField.setPrefWidth(80);

        HBox controls = new HBox(8, new Label("Port:"), portField, startBtn);
        VBox root = new VBox(8, controls, new Label("Server Log:"), logArea);
        root.setPadding(new Insets(10));

        startBtn.setOnAction(e -> {
            if (!running) startServer();
            else stopServer();
        });

        stage.setScene(new Scene(root, 600, 400));
        stage.setTitle("Chat Server");
        stage.setOnCloseRequest(e -> {
            stopServer();
            pool.shutdownNow();
        });
        stage.show();
    }

    private void `Log(String s) {
        Platform.runLater(() -> {
            logArea.appendText(s + "\n");
        });
    }

    private void startServer() {
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException ex) {
            appendLog("Invalid port.");
            return;
        }
        running = true;
        startBtn.setText("Stop Server");
        appendLog("Starting server on port " + port + " ...");

        pool.submit(() -> {
            try (ServerSocket ss = new ServerSocket(port)) {
                serverSocket = ss;
                appendLog("Server listening on " + ss.getLocalSocketAddress());
                while (running) {
                    try {
                        Socket s = ss.accept();
                        ClientHandler ch = new ClientHandler(s);
                        clients.add(ch);
                        pool.submit(ch);
                    } catch (SocketException se) {
                        // ServerSocket closed; exit accept loop
                        break;
                    } catch (IOException ioe) {
                        appendLog("Accept error: " + ioe.getMessage());
                    }
                }
            } catch (IOException e) {
                appendLog("Could not start server: " + e.getMessage());
                Platform.runLater(() -> stopServer());
            }
        });
    }

    private void stopServer() {
        running = false;
        startBtn.setText("Start Server");
        appendLog("Stopping server...");
        try {
            if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
        } catch (IOException ignored) { }
        // close clients
        for (ClientHandler c : clients) c.shutdown();
        clients.clear();
        appendLog("Server stopped.");
    }

   // Broadcast to everyone (including sender). Server still logs but keeps it separate.
private void broadcast(String msg, ClientHandler ignored) {
    // Optional: keep server log but not as the authoritative chat stream
    appendLog("[server] " + msg);

    for (ClientHandler c : clients) {
        c.send(msg); // send to all clients, including the one that sent it
    }
}

    private class ClientHandler implements Runnable {
        private final Socket s;
        private PrintWriter out;
        private BufferedReader in;
        private String name = "User";

        ClientHandler(Socket socket) {
            this.s = socket;
        }

        @Override
        public void run() {
            appendLog("Connection from " + s.getRemoteSocketAddress());
            try {
                out = new PrintWriter(s.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(s.getInputStream()));
                out.println("Welcome! Type your name:");
                String n = in.readLine();
                if (n != null && !n.isBlank()) name = n.trim();
                String join = name + " joined from " + s.getRemoteSocketAddress();
                broadcast(join, this);

                String line;
                while ((line = in.readLine()) != null) {
                    if (line.equalsIgnoreCase("/quit")) break;
                    String msg = name + ": " + line;
                    broadcast(msg, this);
                }
            } catch (IOException e) {
                appendLog("Client error: " + e.getMessage());
            } finally {
                shutdown();
                clients.remove(this);
                String left = name + " left.";
                broadcast(left, this);
                appendLog("Closed connection: " + s.getRemoteSocketAddress());
            }
        }

        void send(String msg) {
            if (out != null) out.println(msg);
        }

        void shutdown() {
            try { if (out != null) out.close(); } catch (Exception ignored) {}
            try { if (in != null) in.close(); } catch (Exception ignored) {}
            try { if (s != null && !s.isClosed()) s.close(); } catch (IOException ignored) {}
        }
    }
}

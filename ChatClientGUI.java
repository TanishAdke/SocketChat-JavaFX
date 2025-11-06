import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.*;
import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatClientGUI extends Application {
    private TextArea chatArea;
    private TextField inputField;
    private Button sendBtn, connectBtn;
    private TextField ipField, portField, nameField;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private ExecutorService pool = Executors.newSingleThreadExecutor();

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        chatArea = new TextArea();
        chatArea.setEditable(false);
        chatArea.setWrapText(true);

        inputField = new TextField();
        inputField.setPrefWidth(400);
        sendBtn = new Button("Send");
        sendBtn.setDisable(true);

        ipField = new TextField("127.0.0.1");
        ipField.setPrefWidth(120);
        portField = new TextField("12345");
        portField.setPrefWidth(80);
        nameField = new TextField("Tanish");
        nameField.setPrefWidth(120);
        connectBtn = new Button("Connect");

        HBox top = new HBox(8, new Label("Server IP:"), ipField, new Label("Port:"), portField,
                new Label("Name:"), nameField, connectBtn);
        top.setPadding(new Insets(6));

        HBox bottom = new HBox(6, inputField, sendBtn);
        bottom.setPadding(new Insets(6));

        VBox root = new VBox(6, top, chatArea, bottom);
        root.setPadding(new Insets(8));

        connectBtn.setOnAction(e -> {
            if (socket == null || socket.isClosed()) connect();
            else disconnect();
        });

        sendBtn.setOnAction(e -> sendMessage());
        inputField.setOnKeyPressed(ev -> {
            if (ev.getCode() == KeyCode.ENTER) sendMessage();
        });

        stage.setScene(new Scene(root, 700, 450));
        stage.setTitle("Chat Client");
        stage.setOnCloseRequest(e -> {
            disconnect();
            pool.shutdownNow();
        });
        stage.show();
    }

    private void appendChat(String s) {
        Platform.runLater(() -> {
            chatArea.appendText(s + "\n");
        });
    }

    private void connect() {
        String ip = ipField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException ex) {
            appendChat("Invalid port.");
            return;
        }
        String name = nameField.getText().trim();
        connectBtn.setDisable(true);
        appendChat("Connecting to " + ip + ":" + port + " ...");

        pool.submit(() -> {
            try {
                socket = new Socket();
                socket.connect(new InetSocketAddress(ip, port), 5000);
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                // read welcome prompt then send name
                String welcome = in.readLine();
                appendChat("[server] " + welcome);
                out.println(name);

                // start reader loop
                sendBtn.setDisable(false);
                Platform.runLater(() -> connectBtn.setText("Disconnect"));
                String line;
                while ((line = in.readLine()) != null) {
                    appendChat(line);
                }
            } catch (IOException e) {
                appendChat("Connection error: " + e.getMessage());
            } finally {
                disconnect();
            }
        });
    }

 private void sendMessage() {
    String text = inputField.getText();
    if (text == null || text.isBlank()) return;

    // Show locally immediately (use the user's name prefix to match incoming messages)
    String name = nameField.getText().trim();
    appendChat((name.isEmpty() ? "Me" : name) + ": " + text);

    if (out != null) {
        out.println(text); // send to server for other clients
        inputField.clear();
        if (text.equalsIgnoreCase("/quit")) disconnect();
    } else {
        appendChat("Not connected.");
    }
}

    private void disconnect() {
        try {
            if (out != null) out.println("/quit");
        } catch (Exception ignored) {}
        try { if (in != null) in.close(); } catch (IOException ignored) {}
        try { if (out != null) out.close(); } catch (Exception ignored) {}
        try { if (socket != null && !socket.isClosed()) socket.close(); } catch (IOException ignored) {}
        out = null; in = null; socket = null;
        Platform.runLater(() -> {
            sendBtn.setDisable(true);
            connectBtn.setText("Connect");
            connectBtn.setDisable(false);
        });
        appendChat("Disconnected.");
    }
}

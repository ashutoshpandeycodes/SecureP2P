import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SecureP2PGUI extends JFrame {

    private static final String ALGORITHM_AES = "AES";
    private static final String ALGORITHM_RSA = "RSA";
    private static final ExecutorService threadPool = Executors.newFixedThreadPool(10);

    // UI Components
    private static JTextArea logArea;
    private JTextField serverPortField, targetIpField, targetPortField;
    private JButton startServerBtn, selectFileBtn, sendFileBtn;
    private File selectedFile;
    private JLabel selectedFileLabel;

    public SecureP2PGUI() {
        setTitle("Secure P2P File Sharing");
        setSize(600, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // --- TOP PANEL: Server Setup ---
        JPanel serverPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        serverPanel.setBorder(BorderFactory.createTitledBorder("1. Receiver (Server Setup)"));
        serverPanel.add(new JLabel("Listen on Port:"));
        serverPortField = new JTextField("8080", 5);
        serverPanel.add(serverPortField);

        startServerBtn = new JButton("Start Listening");
        startServerBtn.addActionListener(e -> startServer());
        serverPanel.add(startServerBtn);

        // --- MIDDLE PANEL: Client Setup ---
        JPanel clientPanel = new JPanel(new GridLayout(3, 1, 5, 5));
        clientPanel.setBorder(BorderFactory.createTitledBorder("2. Sender (Client Setup)"));

        JPanel targetPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        targetPanel.add(new JLabel("Target IP:"));
        targetIpField = new JTextField("127.0.0.1", 10);
        targetPanel.add(targetIpField);
        targetPanel.add(new JLabel("Target Port:"));
        targetPortField = new JTextField("8081", 5);
        targetPanel.add(targetPortField);
        clientPanel.add(targetPanel);

        JPanel filePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        selectFileBtn = new JButton("Select File...");
        selectedFileLabel = new JLabel("No file selected.");
        selectFileBtn.addActionListener(e -> chooseFile());
        filePanel.add(selectFileBtn);
        filePanel.add(selectedFileLabel);
        clientPanel.add(filePanel);

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        sendFileBtn = new JButton("Send File securely");
        sendFileBtn.setEnabled(false);
        sendFileBtn.addActionListener(e -> sendFile());
        actionPanel.add(sendFileBtn);
        clientPanel.add(actionPanel);

        // --- COMBINE TOP AND MIDDLE ---
        JPanel controlPanel = new JPanel(new BorderLayout());
        controlPanel.add(serverPanel, BorderLayout.NORTH);
        controlPanel.add(clientPanel, BorderLayout.CENTER);
        add(controlPanel, BorderLayout.NORTH);

        // --- BOTTOM PANEL: Logs ---
        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("3. Transfer Logs & Verification"));
        add(scrollPane, BorderLayout.CENTER);
    }

    // --- GUI ACTIONS ---
    private void startServer() {
        try {
            int port = Integer.parseInt(serverPortField.getText().trim());
            threadPool.execute(new PeerServer(port));
            startServerBtn.setEnabled(false);
            serverPortField.setEnabled(false);
        } catch (NumberFormatException ex) {
            log("[Error] Invalid port number.");
        }
    }

    private void chooseFile() {
        JFileChooser fileChooser = new JFileChooser();
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            selectedFile = fileChooser.getSelectedFile();
            selectedFileLabel.setText(selectedFile.getName());
            sendFileBtn.setEnabled(true);
        }
    }

    private void sendFile() {
        String ip = targetIpField.getText().trim();
        try {
            int port = Integer.parseInt(targetPortField.getText().trim());
            if (selectedFile != null && selectedFile.exists()) {
                threadPool.execute(new FileSender(ip, port, selectedFile));
            }
        } catch (NumberFormatException ex) {
            log("[Error] Invalid target port.");
        }
    }

    public static synchronized void log(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public static void main(String[] args) {
        // Use standard system look and feel
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            new SecureP2PGUI().setVisible(true);
            log("=== Secure P2P Node Initialized ===");
            log("1. Enter a port and click 'Start Listening' to receive files.");
            log("2. To send, enter a target IP and Port, select a file, and hit Send.\n");
        });
    }

    // ==========================================
    // SERVER & CLIENT LOGIC (Same core engine as before)
    // ==========================================
    static class PeerServer implements Runnable {
        private final int port;
        public PeerServer(int port) { this.port = port; }

        @Override
        public void run() {
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                log("[Server] Listening for incoming files on port " + port);
                while (!Thread.currentThread().isInterrupted()) {
                    Socket clientSocket = serverSocket.accept();
                    log("\n[Server] Incoming connection from " + clientSocket.getInetAddress());
                    threadPool.execute(new ClientHandler(clientSocket));
                }
            } catch (IOException e) {
                log("[Server] Error: " + e.getMessage());
            }
        }
    }

    static class ClientHandler implements Runnable {
        private final Socket socket;
        public ClientHandler(Socket socket) { this.socket = socket; }

        @Override
        public void run() {
            try (DataInputStream dis = new DataInputStream(socket.getInputStream());
                 DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

                log("[Server] Handshake: Generating RSA Keys...");
                KeyPair rsaKeys = CryptoUtils.generateRSAKeyPair();
                byte[] pubKeyBytes = rsaKeys.getPublic().getEncoded();
                dos.writeInt(pubKeyBytes.length);
                dos.write(pubKeyBytes);

                int encryptedAesKeyLen = dis.readInt();
                byte[] encryptedAesKey = new byte[encryptedAesKeyLen];
                dis.readFully(encryptedAesKey);

                SecretKey aesKey = CryptoUtils.decryptAESKey(encryptedAesKey, rsaKeys.getPrivate());
                log("[Server] Handshake: AES Session Key secured.");

                String fileName = dis.readUTF();
                long fileSize = dis.readLong();
                String senderHash = dis.readUTF();

                log("[Server] Receiving: " + fileName + " (" + (fileSize/1024) + " KB)");

                File outputFile = new File("received_" + fileName);
                try (FileOutputStream fos = new FileOutputStream(outputFile);
                     CipherInputStream cis = new CipherInputStream(dis, CryptoUtils.getAESCipher(Cipher.DECRYPT_MODE, aesKey))) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = cis.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                    }
                }

                String receiverHash = CryptoUtils.calculateSHA256(outputFile);
                if (senderHash.equals(receiverHash)) {
                    log("[Server] SUCCESS: " + fileName + " verified. SHA-256 match.");
                } else {
                    log("[Server] WARNING: File corrupted! Hashes mismatch.");
                }
            } catch (Exception e) {
                log("[Server] Transfer failed: " + e.getMessage());
            } finally {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
    }

    static class FileSender implements Runnable {
        private final String targetIp;
        private final int targetPort;
        private final File fileToSend;

        public FileSender(String targetIp, int targetPort, File file) {
            this.targetIp = targetIp;
            this.targetPort = targetPort;
            this.fileToSend = file;
        }

        @Override
        public void run() {
            try (Socket socket = new Socket(targetIp, targetPort);
                 DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                 DataInputStream dis = new DataInputStream(socket.getInputStream())) {

                log("[Client] Connected to " + targetIp + ":" + targetPort);
                log("[Client] Hashing file (SHA-256)...");
                String fileHash = CryptoUtils.calculateSHA256(fileToSend);

                int pubKeyLen = dis.readInt();
                byte[] pubKeyBytes = new byte[pubKeyLen];
                dis.readFully(pubKeyBytes);
                PublicKey serverPubKey = CryptoUtils.getPublicKeyFromBytes(pubKeyBytes);

                log("[Client] Encrypting AES session key with Server's RSA Public Key...");
                SecretKey aesKey = CryptoUtils.generateAESKey();
                byte[] encryptedAesKey = CryptoUtils.encryptAESKey(aesKey, serverPubKey);
                dos.writeInt(encryptedAesKey.length);
                dos.write(encryptedAesKey);

                dos.writeUTF(fileToSend.getName());
                dos.writeLong(fileToSend.length());
                dos.writeUTF(fileHash);

                log("[Client] Uploading encrypted stream...");
                try (FileInputStream fis = new FileInputStream(fileToSend);
                     CipherOutputStream cos = new CipherOutputStream(dos, CryptoUtils.getAESCipher(Cipher.ENCRYPT_MODE, aesKey))) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        cos.write(buffer, 0, bytesRead);
                    }
                }
                log("[Client] Transfer complete: " + fileToSend.getName());

            } catch (Exception e) {
                log("[Client] Transfer failed: " + e.getMessage());
            }
        }
    }

    static class CryptoUtils {
        public static KeyPair generateRSAKeyPair() throws Exception {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance(ALGORITHM_RSA);
            keyGen.initialize(2048);
            return keyGen.generateKeyPair();
        }
        public static PublicKey getPublicKeyFromBytes(byte[] keyBytes) throws Exception {
            java.security.spec.X509EncodedKeySpec spec = new java.security.spec.X509EncodedKeySpec(keyBytes);
            return KeyFactory.getInstance(ALGORITHM_RSA).generatePublic(spec);
        }
        public static SecretKey generateAESKey() throws Exception {
            KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM_AES);
            keyGen.init(128);
            return keyGen.generateKey();
        }
        public static byte[] encryptAESKey(SecretKey aesKey, PublicKey rsaPublicKey) throws Exception {
            Cipher cipher = Cipher.getInstance(ALGORITHM_RSA);
            cipher.init(Cipher.ENCRYPT_MODE, rsaPublicKey);
            return cipher.doFinal(aesKey.getEncoded());
        }
        public static SecretKey decryptAESKey(byte[] encryptedAesKey, PrivateKey rsaPrivateKey) throws Exception {
            Cipher cipher = Cipher.getInstance(ALGORITHM_RSA);
            cipher.init(Cipher.DECRYPT_MODE, rsaPrivateKey);
            byte[] decryptedKey = cipher.doFinal(encryptedAesKey);
            return new SecretKeySpec(decryptedKey, 0, decryptedKey.length, ALGORITHM_AES);
        }
        public static Cipher getAESCipher(int mode, SecretKey key) throws Exception {
            Cipher cipher = Cipher.getInstance(ALGORITHM_AES);
            cipher.init(mode, key);
            return cipher;
        }
        public static String calculateSHA256(File file) throws Exception {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = fis.read(buffer)) != -1) { digest.update(buffer, 0, n); }
            }
            byte[] hashBytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) { sb.append(String.format("%02x", b)); }
            return sb.toString();
        }
    }
}

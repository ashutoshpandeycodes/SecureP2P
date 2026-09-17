import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.*;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SecureP2P {

    private static final String ALGORITHM_AES = "AES";
    private static final String ALGORITHM_RSA = "RSA";

    // Thread pool to handle multiple concurrent uploads/downloads
    private static final ExecutorService threadPool = Executors.newFixedThreadPool(10);

    public static void main(String[] args) {
        System.out.println("=== Secure P2P File Sharing Node ===");
        Scanner scanner = new Scanner(System.in);

        System.out.print("Enter port to listen on (e.g., 8080 for Node A, 8081 for Node B): ");
        int localPort = Integer.parseInt(scanner.nextLine());

        // Start the server listener in a background thread
        threadPool.execute(new PeerServer(localPort));

        while (true) {
            System.out.println("\nOptions: [1] Send File  [2] Exit");
            System.out.print("Choose an option: ");
            String choice = scanner.nextLine();

            if ("1".equals(choice)) {
                System.out.print("Enter target IP (e.g., 127.0.0.1): ");
                String ip = scanner.nextLine();

                System.out.print("Enter target port (e.g., 8080, 8081): ");
                int targetPort = Integer.parseInt(scanner.nextLine());

                System.out.print("Enter absolute file path to send: ");
                String filePath = scanner.nextLine();

                File file = new File(filePath);
                if (file.exists() && !file.isDirectory()) {
                    threadPool.execute(new FileSender(ip, targetPort, file));
                } else {
                    System.out.println("Error: File does not exist at that path.");
                }
            } else if ("2".equals(choice)) {
                System.out.println("Shutting down node...");
                threadPool.shutdownNow();
                System.exit(0);
            }
        }
    }

    // ==========================================
    // 1. SERVER (RECEIVER) LOGIC
    // ==========================================
    static class PeerServer implements Runnable {
        private final int port;

        public PeerServer(int port) {
            this.port = port;
        }

        @Override
        public void run() {
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                System.out.println("[Server] Listening on port " + port);
                while (!Thread.currentThread().isInterrupted()) {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("\n[Server] Incoming connection from " + clientSocket.getInetAddress());
                    threadPool.execute(new ClientHandler(clientSocket));
                }
            } catch (IOException e) {
                System.err.println("[Server] Error: " + e.getMessage());
            }
        }
    }

    static class ClientHandler implements Runnable {
        private final Socket socket;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (
                    DataInputStream dis = new DataInputStream(socket.getInputStream());
                    DataOutputStream dos = new DataOutputStream(socket.getOutputStream())
            ) {
                KeyPair rsaKeys = CryptoUtils.generateRSAKeyPair();
                byte[] pubKeyBytes = rsaKeys.getPublic().getEncoded();
                dos.writeInt(pubKeyBytes.length);
                dos.write(pubKeyBytes);

                int encryptedAesKeyLen = dis.readInt();
                byte[] encryptedAesKey = new byte[encryptedAesKeyLen];
                dis.readFully(encryptedAesKey);

                SecretKey aesKey = CryptoUtils.decryptAESKey(encryptedAesKey, rsaKeys.getPrivate());

                String fileName = dis.readUTF();
                long fileSize = dis.readLong();
                String senderHash = dis.readUTF();

                System.out.println("[Server] Receiving: " + fileName + " (" + fileSize + " bytes)");

                File outputFile = new File("received_" + fileName);
                try (
                        FileOutputStream fos = new FileOutputStream(outputFile);
                        CipherInputStream cis = new CipherInputStream(dis, CryptoUtils.getAESCipher(Cipher.DECRYPT_MODE, aesKey))
                ) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    long totalRead = 0;
                    while (totalRead < fileSize && (bytesRead = cis.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                        totalRead += bytesRead;
                    }
                }

                String receiverHash = CryptoUtils.calculateSHA256(outputFile);
                if (senderHash.equals(receiverHash)) {
                    System.out.println("[Server] SUCCESS: " + fileName + " received and verified (SHA-256 matches).");
                } else {
                    System.err.println("[Server] WARNING: File corrupted during transfer! Hashes mismatch.");
                }

            } catch (Exception e) {
                System.err.println("[Server] Transfer failed: " + e.getMessage());
            } finally {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
    }

    // ==========================================
    // 2. CLIENT (SENDER) LOGIC
    // ==========================================
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

                System.out.println("[Client] Connected to " + targetIp);

                String fileHash = CryptoUtils.calculateSHA256(fileToSend);

                int pubKeyLen = dis.readInt();
                byte[] pubKeyBytes = new byte[pubKeyLen];
                dis.readFully(pubKeyBytes);
                PublicKey serverPubKey = CryptoUtils.getPublicKeyFromBytes(pubKeyBytes);

                SecretKey aesKey = CryptoUtils.generateAESKey();
                byte[] encryptedAesKey = CryptoUtils.encryptAESKey(aesKey, serverPubKey);
                dos.writeInt(encryptedAesKey.length);
                dos.write(encryptedAesKey);

                dos.writeUTF(fileToSend.getName());
                dos.writeLong(fileToSend.length());
                dos.writeUTF(fileHash);

                try (
                        FileInputStream fis = new FileInputStream(fileToSend);
                        CipherOutputStream cos = new CipherOutputStream(dos, CryptoUtils.getAESCipher(Cipher.ENCRYPT_MODE, aesKey))
                ) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        cos.write(buffer, 0, bytesRead);
                    }
                }

                System.out.println("[Client] Transfer complete: " + fileToSend.getName());

            } catch (Exception e) {
                System.err.println("[Client] Transfer failed: " + e.getMessage());
            }
        }
    }

    // ==========================================
    // 3. CRYPTOGRAPHY & UTILITIES
    // ==========================================
    static class CryptoUtils {

        public static KeyPair generateRSAKeyPair() throws Exception {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance(ALGORITHM_RSA);
            keyGen.initialize(2048);
            return keyGen.generateKeyPair();
        }

        public static PublicKey getPublicKeyFromBytes(byte[] keyBytes) throws Exception {
            java.security.spec.X509EncodedKeySpec spec = new java.security.spec.X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM_RSA);
            return keyFactory.generatePublic(spec);
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
                while ((n = fis.read(buffer)) != -1) {
                    digest.update(buffer, 0, n);
                }
            }
            byte[] hashBytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        }
    }
}
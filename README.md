# Secure P2P File Sharing System 

**Developer:** Ashutosh Kumar Pandey  
**Institution:** VIT Bhopal University  
**Program:** B.Tech in Computer Science and Engineering (AI/ML)  

---

## 📌 Executive Summary
The Secure P2P File Sharing System is a decentralized, multithreaded networking application engineered entirely in Core Java. Bypassing the traditional client-server bottleneck, it implements a true Peer-to-Peer (P2P) architecture where every node functions concurrently as both a sender and a receiver. 

Designed for hostile or zero-trust local networks, the system guarantees end-to-end data confidentiality through a hybrid cryptographic handshake and ensures absolute data integrity via cryptographic hashing. This iteration completely replaces the legacy command-line interface with a responsive, thread-safe Java Swing desktop environment.

---

## 🖥️ Graphical User Interface (GUI) Implementation

The graphical interface was built exclusively using **Java Swing** and **AWT (Abstract Window Toolkit)**, ensuring the application remains lightweight and natively executable without requiring external UI libraries like JavaFX.

* **Component Architecture:** The interface extends `JFrame` and utilizes a nested layout strategy. `BorderLayout` dictates the primary window structure, while nested `JPanel` containers use `FlowLayout` and `GridLayout` to cleanly organize the Receiver (Server) and Sender (Client) configuration panels.
* **Native OS Integration:** The application dynamically applies the host system's native look and feel via `UIManager.getSystemLookAndFeelClassName()`, ensuring it looks like a natural Windows application rather than a legacy Java applet.
* **Interactive Elements:** File selection is handled natively via `JFileChooser`, mapping the absolute system path of the payload. Live telemetry and error handling are streamed to a non-editable `JTextArea` wrapped in a `JScrollPane` for real-time monitoring.
* **Event Dispatch Thread (EDT) Safety:** GUI frameworks are inherently single-threaded. To prevent the interface from freezing during a heavy 5GB file transfer, all UI updates triggered by the network layer (such as cryptographic handshakes or progress logs) are asynchronously queued to the EDT using `SwingUtilities.invokeLater()`. This strictly decouples the visual frontend from the blocking I/O backend.

---

## ⚙️ Core Technical Architecture

This project demonstrates advanced software engineering principles, specifically focusing on thread safety, dynamic memory management during I/O operations, and applied cryptography using the Java Cryptography Architecture (JCA).

### 1. Hybrid Cryptographic Engine (End-to-End Security)
Relying on a single encryption method is either too slow (Asymmetric) or insecure for key transmission (Symmetric). This system implements a hybrid handshake:
* **RSA-2048 (Asymmetric):** Secures the initial connection. When Node A connects to Node B, Node B generates a fresh, temporary RSA key pair using `KeyPairGenerator` and transmits its Public Key over the raw socket.
* **AES-128 (Symmetric):** Optimizes bulk data transfer. Node A generates a randomized AES session key (`KeyGenerator`), encrypts it using Node B's RSA Public Key, and transmits it. Both nodes now share a highly secure, private AES key for the remainder of the session without ever exposing it in plain text.

### 2. Concurrent Networking & Thread Management
* **TCP/IP Sockets:** Utilizes `java.net.Socket` and `ServerSocket` for reliable, connection-oriented byte-stream delivery across local or public networks.
* **`ExecutorService` Thread Pooling:** Instead of manually spawning unmanaged `Thread` objects (which is resource-intensive), the backend utilizes `Executors.newFixedThreadPool(10)`. This allows a single node to handle multiple incoming client connections and outgoing file transfers simultaneously in the background.
* **Runnable Tasks:** The core network operations are divided into decoupled `Runnable` classes (`PeerServer`, `ClientHandler`, `FileSender`), allowing the thread pool to execute them completely independently of the main application thread.

### 3. Memory-Safe Data Streaming
* **Dynamic Chunking via Cipher Streams:** Standard file transfers load the entire byte array into memory, causing an `OutOfMemoryError` on large files. This application wraps the raw `DataInputStream` and `DataOutputStream` directly with `CipherInputStream` and `CipherOutputStream`. 
* **Zero-Footprint Streaming:** The file is read from the disk, AES-encrypted on the fly, and pushed over the TCP network in discrete **4KB chunks**. This keeps the RAM footprint near zero, regardless of whether the file is 1MB or 100GB.

### 4. Cryptographic Integrity Verification
* **SHA-256 Hashing (`MessageDigest`):** Network packets can drop, and malicious actors can attempt to tamper with the byte stream mid-transfer. Before transmission, the sender calculates a precise SHA-256 checksum of the original file and sends it as string metadata. 
* **Post-Transfer Validation:** The receiver independently hashes the final decrypted file stored on their disk. The system compares the two hashes; a perfect string match mathematically guarantees the file suffered zero packet corruption or alteration during network transit.

---

## 🚀 Setup & Execution Guide

### Prerequisites
* Java Runtime Environment (JRE) or Java Development Kit (JDK) 11+ installed and added to your system's `PATH`.

### Running the Application
You can run this application by executing the compiled JAR file or directly through an IDE. 

**Option 1: Using the Standalone JAR**
1. Download `SecureP2P.jar` from the GitHub Releases page.
2. Double-click the `.jar` file to instantly launch the graphical interface. 
3. *Alternatively, execute via terminal:* `java -jar SecureP2P.jar`

**Option 2: Running via IDE (Local Network Simulation)**
Because this is a true P2P system, testing it on a single computer requires running two separate instances to act as distinct nodes.
1. Open the project in IntelliJ IDEA.
2. Ensure **"Allow multiple instances"** is enabled in your Run Configurations.
3. Run the `SecureP2PGUI` class to launch **Node A**.
4. Run the `SecureP2PGUI` class a second time to launch **Node B**.

---

## 📖 Usage Guide: Transferring a File

**Step 1: Initialize the Receiver (Node A)**
* On the first window, navigate to the **1. Receiver (Server Setup)** panel.
* Enter a local port to bind to (e.g., `8080`).
* Click **Start Listening**. The node is now ready to accept incoming secure connections.

**Step 2: Initialize the Sender (Node B)**
* On the second window, navigate to the **1. Receiver** panel and bind to a *different* port (e.g., `8081`) so it doesn't conflict with Node A. Click **Start Listening**.

**Step 3: Execute the Transfer (From Node B to Node A)**
* On Node B, navigate to the **2. Sender (Client Setup)** panel.
* **Target IP:** Enter `127.0.0.1` (or the specific local network IP if transferring between two physical computers).
* **Target Port:** Enter `8080` (the port Node A is actively listening on).
* Click **Select File...** to choose a payload from your local machine.
* Click **Send File securely**.

**Step 4: Monitor Live Telemetry**
* The **3. Transfer Logs & Verification** panel on both screens will instantly update.
* Watch the cryptographic handshake occur in real-time, followed by the progressive file transfer. 
* Upon completion, the receiver node will print a `SUCCESS` message confirming the SHA-256 integrity match. The file is saved directly to the application's root execution directory with a `received_` prefix.

# Secure P2P File Sharing System

**Developer:** Ashutosh Kumar Pandey  
**Institution:** VIT Bhopal University  
**Program:** B.Tech in Computer Science and Engineering (AI/ML)

---

##  Project Overview
The Secure P2P File Sharing System is a decentralized, multithreaded networking application built entirely in Core Java. Bypassing the traditional client-server architecture, this system implements a true Peer-to-Peer (P2P) model where every node acts as both a sender and a receiver.

To ensure complete data security and privacy over public or local networks, the application implements a hybrid cryptographic handshake (RSA + AES) and verifies data integrity post-transfer using SHA-256 hashing.

###  Why a JAR Executable? (Usability & Deployment)
This project is packaged as a **Standalone Executable JAR (Java ARchive)**. This architectural decision was made to significantly improve usability, convenience, and deployment flexibility:
1. **Zero-Configuration Deployment:** Evaluators and end-users do not need to install an IDE (like IntelliJ or Eclipse), configure build paths, or manage run configurations to test the application.
2. **Cross-Platform Compatibility:** The JAR file encapsulates all compiled classes and can be run instantly on any operating system (Windows, macOS, Linux) via the command line, provided the Java Runtime Environment (JRE) is installed.
3. **True P2P Simulation:** By using a JAR, users can effortlessly copy the single file to multiple different computers on the same Local Area Network (LAN) and execute them independently, perfectly simulating a real-world decentralized network without needing internet access.

---

## ️Core Architecture & Technical Implementation

### 1. Hybrid Cryptography (End-to-End Security)
* **RSA (2048-bit Asymmetric):** Used for secure session key exchange. Upon connection, the receiving node generates a temporary RSA key pair and transmits its public key to the sender.
* **AES (128-bit Symmetric):** Used for bulk file encryption. The sender generates a random AES session key, encrypts it using the receiver's RSA public key, and transmits it. Both peers then share a secure channel.

### 2. Concurrent Networking & Threading
* **Socket TCP/IP:** Utilizes `java.net.Socket` and `ServerSocket` for reliable data streaming.
* **Thread Pooling (`ExecutorService`):** The engine manages a pool of background threads. A node can handle multiple incoming file downloads concurrently while actively sending a file to another peer, completely avoiding main-thread blocking.

### 3. Memory-Efficient I/O & Integrity Check
* **Cipher Streams:** Wraps the socket streams with `CipherInputStream` and `CipherOutputStream`. Files are read, encrypted, and transmitted in dynamic 4KB chunks. This prevents `OutOfMemoryError`, allowing the smooth transfer of large multi-gigabyte files.
* **SHA-256 Hashing:** The sender hashes the original file and transmits the checksum. The receiver hashes the final downloaded file and compares the results. A match mathematically guarantees the file suffered zero packet corruption or tampering during network transit.

---

## Setup & Execution Guide

### Prerequisites
* Java Runtime Environment (JRE) 11 or higher installed on your machine.

### Running the Application
Since this is a standalone JAR, you can run it directly from your terminal or command prompt.

1. Open your terminal/command prompt.
2. Navigate to the directory containing the JAR file.
3. Execute the following command:
   ```bash
   java -jar SecureP2P.jar
   ```

*(Note: To test locally on a single machine, open two separate terminal windows and run the command in both to simulate two different nodes).*

---

## Usage Guide: Transferring a File

**1. Start Node A (Receiver)**
* Run the JAR in Terminal 1.
* When prompted, enter a port to listen on (e.g., `8080`).

**2. Start Node B (Sender)**
* Run the JAR in Terminal 2.
* When prompted, enter a different port (e.g., `8081`).

**3. Initiate Transfer (from Node B)**
* In Terminal 2, type `1` to select "Send File".
* **Target IP:** Enter `127.0.0.1` (or the local LAN IP if using a second physical computer).
* **Target Port:** Enter `8080` (Node A's port).
* **File Path:** Enter the absolute path to a file on your system (e.g., `C:\Users\Name\Documents\assignment.pdf`).

**4. Monitor & Verify**
* Node A's terminal will display the incoming connection, the RSA public key exchange, the AES session establishment, and the file transfer progress.
* Upon completion, Node A will perform a SHA-256 integrity check and print a `SUCCESS` message.
* The file will be saved in the directory where Node A was executed, prefixed with `received_`.
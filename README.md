### FITNet 
Network Collaboration Platform

FITNet is a web-based collaboration platform that allows users to communicate, discover online users, share announcements, transfer files securely, and access remote resources.

The project demonstrates how different network programming technologies can work together in one client-server application using Java.

---

## 1. Project Overview

FITNet provides five main networking features:

| Module | Function | Networking Technology |
|---|---|---|
| TCP Messaging | Private and group messaging | TCP Sockets |
| UDP Presence | Online and offline user discovery | UDP Datagrams |
| Multicast Announcements | Send announcements to multiple users | IP Multicast |
| Secure File Transfer | Encrypted file transfers | SSL/TLS Sockets |
| Remote Resources | Access shared resources remotely | Java RMI and URL Connections |

### Project Objectives

- Understand client-server architecture.
- Implement TCP and UDP communication using Java.
- Support multiple clients communicating with a server.
- Develop real-time messaging and user discovery.
- Implement multicast communication.
- Provide secure file transfers using TLS.
- Access remote services using Java RMI and URL connections.
- Integrate different networking technologies into one application.

---

## 2. System Architecture

FITNet follows a modular client-server architecture.

The Next.js frontend provides the user interface, while the Java Gateway connects browser requests to the individual Java networking modules.

### Architecture Diagram

```
                     FITNet USERS
                          |
                     Web Browser
                          |
                 Next.js / React
                     Frontend
                          |
                   HTTP / WebSocket
                          |
                    JAVA GATEWAY
                      Port 8080
                          |
       +------------------+------------------+
       |                  |                  |
       |                  |                  |
  TCP Messaging      UDP Presence      Multicast
   Port 5001          Port 5002        Port 5003
       |                  |                  |
  TCP Server          UDP Server       Multicast
  Socket              Datagram         Communication
                      Socket
       |
       |
  Secure Transfer                  Remote Resources
    Port 5004                       RMI Registry
       |                            Port 1099
  TLS Server                            |
  SSLServerSocket                  RMI Service
                                    Port 1100
                                        |
                                  URL Connections
```

### How It Works

1. Users access FITNet through a web browser.
2. The frontend sends requests to the Java Gateway through HTTP or WebSocket.
3. The Gateway communicates with the relevant networking module.
4. The Java module performs the required network operation.
5. The result is returned to the frontend through the Gateway.

**Important:** Browsers cannot directly create Java TCP sockets, UDP datagram sockets, or Java RMI connections. The Gateway acts as the bridge between the browser and Java backend services. Actual networking operations must be implemented using the corresponding Java networking technologies.

---

## 3. Technology Stack

### Frontend

| Technology | Purpose |
|---|---|
| Next.js | Frontend framework |
| React | Component-based UI |
| TypeScript | Type-safe development |
| Tailwind CSS | Styling and responsive layouts |
| Lucide Icons | Interface icons |
| WebSocket API | Real-time communication |
| HTTP / Fetch API | Backend API communication |

### Backend

| Technology | Purpose |
|---|---|
| Java 21+ | Main backend language |
| Maven | Build and dependency management |
| ServerSocket / Socket | TCP communication |
| DatagramSocket / DatagramPacket | UDP communication |
| MulticastSocket | Multicast communication |
| SSLServerSocket / SSLSocket | Secure file transfer |
| Java RMI | Remote method invocation |
| URL / URLConnection | Remote resource access |
| ExecutorService | Concurrent client handling |
| JUnit 5 | Backend testing |

### Development Tools

- Visual Studio Code / IntelliJ IDEA – Development
- Git and GitHub – Version control and collaboration
- Postman – API testing
- Browser Developer Tools – Frontend debugging
- Maven CLI – Backend build and testing

### Data Storage

The initial implementation uses in-memory Java collections. A database is not required for the current project scope.

---

## 4. Project Structure

```
FITNet/
│
├── frontend/
│   ├── public/
│   ├── src/
│   │   ├── app/
│   │   ├── components/
│   │   ├── hooks/
│   │   ├── services/
│   │   ├── types/
│   │   └── lib/
│   └── package.json
│
├── backend/
│   ├── pom.xml
│   ├── shared/
│   ├── gateway/
│   ├── tcp-messaging/
│   ├── udp-presence/
│   ├── multicast-announcements/
│   ├── secure-transfer/
│   └── remote-resources/
│
├── database/
├── docs/
└── README.md
```

### Backend Module Responsibilities

- **shared:** Common message models, constants, and reusable utilities.
- **gateway:** HTTP and WebSocket communication with the frontend.
- **tcp-messaging:** TCP-based messaging.
- **udp-presence:** Online user discovery and heartbeat monitoring.
- **multicast-announcements:** Multicast announcement communication.
- **secure-transfer:** TLS-based file transfer.
- **remote-resources:** Java RMI and URL-based resource access.



## 5. Team Contribution and Task Distribution

The project is divided among five members. Each member is responsible for one major networking module, including its implementation, testing, and integration requirements.

| Member | Assigned Module | Main Technology |
|---|---|---|
| Member 01 | TCP Messaging | TCP Sockets |
| Member 02 | UDP Presence | UDP Datagrams |
| Member 03 | Multicast Announcements | IP Multicast |
| Member 04 | Secure File Transfer | SSL/TLS |
| Member 05 | Remote Resources | Java RMI and URL Connections |

### Member 01 – TCP Messaging

**Objective:** Implement private and group messaging using TCP.

Tasks:
1. Create a TCP server using `ServerSocket`.
2. Establish client connections using `Socket`.
3. Support multiple clients simultaneously.
4. Handle concurrent clients using threads or `ExecutorService`.
5. Implement private and group messaging.
6. Manage message sending and receiving through input/output streams.
7. Handle client connections and disconnections.
8. Integrate the module with the Java Gateway and frontend.
9. Test communication between multiple clients.

**Expected demonstration:** Three or more clients communicate through the TCP server simultaneously.

### Member 02 – UDP Presence

**Objective:** Discover online and offline users using UDP.

Tasks:
1. Implement communication using `DatagramSocket`.
2. Create and process `DatagramPacket` messages.
3. Implement periodic heartbeat messages.
4. Maintain an in-memory online user registry.
5. Detect inactive users using heartbeat timeouts.
6. Update user presence information.
7. Integrate the module with the Java Gateway and frontend.
8. Test online and offline detection.

**Expected demonstration:** Users appear online when sending heartbeats and become offline when their heartbeat stops.

### Member 03 – Multicast Announcements

**Objective:** Send announcements to multiple users using IP multicast.

Tasks:
1. Implement a multicast sender.
2. Create multicast receivers.
3. Configure a multicast IP group and port.
4. Implement joining and leaving multicast groups.
5. Send and receive announcement messages.
6. Support multiple receivers.
7. Integrate the module with the Java Gateway and frontend.
8. Test communication with multiple multicast clients.

**Expected demonstration:** One sender publishes an announcement that multiple receivers receive.

### Member 04 – Secure File Transfer

**Objective:** Transfer files securely using TLS.

Tasks:
1. Generate and configure TLS certificates and keystores.
2. Implement a secure server using `SSLServerSocket`.
3. Establish client connections using `SSLSocket`.
4. Implement encrypted communication.
5. Support file upload and download.
6. Transfer file metadata and binary content.
7. Verify file integrity using SHA-256.
8. Handle transfer errors and connection failures.
9. Integrate the module with the Java Gateway and frontend.
10. Test secure file transfers.

**Expected demonstration:** Transfer a file through an encrypted TLS connection and verify that the received file matches the original.

### Member 05 – Remote Resources

**Objective:** Access remote services and resources using Java RMI and URL connections.

Tasks:
1. Define remote interfaces.
2. Implement RMI remote services.
3. Configure the RMI Registry.
4. Register and expose remote objects.
5. Implement remote method calls from clients.
6. Retrieve information from remote services.
7. Implement URL-based resource access using `URL`.
8. Retrieve remote content using `URLConnection`.
9. Implement custom protocol and content handlers where appropriate.
10. Integrate the module with the Java Gateway and frontend.
11. Test remote method invocation and URL-based resource retrieval.

**Expected demonstration:** A client invokes a remote Java method and retrieves content from a URL-based resource.

### Shared Team Responsibilities

All members must:
- Follow the agreed architecture and coding conventions.
- Use consistent message formats where applicable.
- Document their module and integration requirements.
- Test their own implementation before integration.
- Review and test changes during integration.
- Fix issues related to their assigned modules.

The Java Gateway and shared frontend design are integration components. Their ownership and implementation tasks should be agreed upon by the team rather than assumed to belong to one module owner.

---

## 6. Communication Format

Where appropriate, modules can use a common JSON message structure.

Example:

```json
{
  "messageId": "unique-message-id",
  "type": "PRIVATE_MESSAGE",
  "senderId": "user-001",
  "senderName": "Student One",
  "receiverId": "user-002",
  "groupId": null,
  "content": "Hello! Welcome to FITNet.",
  "timestamp": "2026-11-13T10:30:00Z"
}
```

| Field | Description |
|---|---|
| `messageId` | Unique message identifier |
| `type` | Message or event type |
| `senderId` | Sender identifier |
| `senderName` | Sender name |
| `receiverId` | Receiver identifier, when applicable |
| `groupId` | Group identifier, when applicable |
| `content` | Message content |
| `timestamp` | Message creation time in UTC |

Each module may define additional fields according to its requirements.

For TCP communication, use clear message framing, such as newline-delimited JSON. Secure file transfers must use a defined binary-safe protocol for metadata and file content.

---

## 7. Network Configuration

Proposed local development ports:

| Service | Protocol | Port / Address |
|---|---|---|
| Java Gateway | HTTP / WebSocket | 8080 |
| TCP Messaging | TCP | 5001 |
| UDP Presence | UDP | 5002 |
| Multicast Announcements | UDP Multicast | 239.10.10.10:5003 |
| Secure File Transfer | TLS over TCP | 5004 |
| RMI Registry | TCP | 1099 |
| RMI Remote Service | TCP | 1100 |

These values must be configured consistently across the modules. Multicast communication may depend on the network interface, firewall, and network configuration.

---

## 8. Development Setup

### Prerequisites

Install:
- JDK 21 or later
- Apache Maven 3.9 or later
- Node.js and npm
- Git
- Visual Studio Code or IntelliJ IDEA

### Clone the Repository

```bash
git clone https://github.com/Rjkl003CR/FITNet.git
cd FITNet
```

### Frontend Setup

```bash
cd frontend
npm install
npm run dev
```

Open:

```text
http://localhost:3000
```

### Backend Setup

Open another terminal:

```bash
cd backend
mvn clean install
```

Run the Gateway and required networking modules according to their implementation and configuration.

---

## 9. Git Workflow

FITNet uses GitHub for collaborative development.

### Main Branches

| Branch | Purpose |
|---|---|
| `main` | Stable project version |
| `dev` | Shared development and integration |

### Feature Branches

| Module | Branch |
|---|---|
| TCP Messaging | `feature/tcp_messaging` |
| UDP Presence | `feature/udp_presence` |
| Multicast Announcements | `feature/multicast_announcements` |
| Secure File Transfer | `feature/secure_transfer` |
| Remote Resources | `feature/remote_resources` |

### Development Workflow

1. Switch to your assigned feature branch.
2. Pull the latest changes.
3. Implement and test your module.
4. Commit your changes.
5. Push your feature branch.
6. Create a Pull Request to `dev`.
7. Review and test before merging.
8. Keep your feature branch updated with `dev`.
9. Merge stable changes into `main when the team agrees.

Example:

```bash
git switch feature/tcp_messaging
git pull origin feature/tcp_messaging

git add .
git commit -m "Implement TCP messaging functionality"
git push origin feature/tcp_messaging
```

Do not push unfinished feature work directly to `main`.

---

## 10. Testing Strategy

Each module must be tested independently before integration.

| Module | Testing Scenario |
|---|---|
| TCP Messaging | Multiple clients send and receive messages simultaneously |
| UDP Presence | Heartbeats update online/offline status |
| Multicast Announcements | Multiple receivers receive the same announcement |
| Secure File Transfer | TLS transfer and SHA-256 integrity verification |
| Remote Resources | RMI calls and URL-based content retrieval |

After individual testing, the team must verify the complete frontend-to-Gateway-to-backend communication flow.

---

## 11. Expected Outcome

FITNet will provide one integrated web application demonstrating five networking technologies:

- TCP for reliable client-server messaging.
- UDP for lightweight user presence updates.
- Multicast for one-to-many announcements.
- TLS for encrypted file transfers.
- RMI and URL connections for remote resource access.

The final application demonstrates how different networking technologies can be selected and combined to build a practical networked system.

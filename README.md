# FITNet

Network-Based Student Collaboration and Resource Sharing Platform



\# FITNet – Faculty of Information Technology Network Collaboration Platform



FITNet is a web-based student collaboration and resource-sharing platform developed as a group project for the Network Programming module at the University of Moratuwa, Faculty of Information Technology.



The platform demonstrates Java network programming concepts through real-time communication, online presence detection, multicast announcements, secure file transfer, and remote resource sharing.



\## Project Objectives



\- Apply client-server architecture using Java.

\- Implement TCP and UDP communication.

\- Support multiple simultaneous clients.

\- Demonstrate multicast communication.

\- Implement secure communication using TLS.

\- Explore Java Remote Method Invocation (RMI).

\- Use URL connections and custom protocol/content handlers.

\- Integrate Java networking services with a modern web frontend.



\## Technology Stack



| Area | Technologies |

|---|---|

| Frontend | Next.js, React, TypeScript, Tailwind CSS |

| Backend | Java, Maven |

| Networking | Java Sockets, UDP, Multicast, JSSE/TLS, RMI |

| Communication | HTTP, REST, WebSocket |

| Initial Data Storage | In-memory Java collections |

| Version Control | Git and GitHub |



\## System Modules



| Member | Module | Main Technologies |

|---|---|---|

| 01 | TCP Messaging | ServerSocket, Socket, Multithreading |

| 02 | UDP Presence | DatagramSocket, DatagramPacket |

| 03 | Multicast Announcements | MulticastSocket |

| 04 | Secure File Transfer | SSLServerSocket, SSLSocket |

| 05 | Remote Resources | Java RMI, URL, URLConnection, Protocol and Content Handlers |



\## Project Structure



```text

FITNet/

├── frontend/                  # Next.js application

├── backend/

│   ├── pom.xml                # Maven parent project

│   ├── shared/

│   ├── gateway/

│   ├── tcp-messaging/

│   ├── udp-presence/

│   ├── multicast-announcements/

│   ├── secure-transfer/

│   └── remote-resources/

├── database/                  # Future database configuration

├── docs/

│   ├── architecture/

│   ├── api/

│   ├── networking/

│   └── testing/

├── .gitignore

├── .env.example

└── README.md

```



\## Getting Started



\### Prerequisites



\- JDK 21 or later

\- Apache Maven 3.9+

\- Node.js and npm

\- Git



\### 1. Clone the repository



```bash

git clone https://github.com/Rjkl003CR/FITNet.git

cd FITNet

```



\### 2. Run the frontend



```bash

cd frontend

npm install

npm run dev

```



Open http://localhost:3000 in your browser.



\### 3. Build the Java backend



From the project root:



```bash

cd backend

mvn clean install

```



\### 4. Run networking services



Each networking module will be configured to run independently during development. Refer to the module documentation for its execution instructions.



\## Development Workflow



The project follows a feature-branch Git workflow.



\- `main` – Stable project version.

\- `dev` – Shared integration branch.

\- `feature/tcp\_messaging`

\- `feature/udp\_presence`

\- `feature/multicast`

\- `feature/secure\_transfer`

\- `feature/remote\_resource`



Each member develops their assigned module in a feature branch and submits a Pull Request to `dev` for integration.



\## Project Information



\*\*University:\*\* University of Moratuwa  

\*\*Faculty:\*\* Faculty of Information Technology  

\*\*Module:\*\* Network Programming  

\*\*Project:\*\* FITNet  

\*\*Team Size:\*\* 5 Members



\## Project Status



Currently in the initial development and project setup phase.



More features, implementation details, and documentation will be added as development progresses.



\---



Developed as an academic group project at the University of Moratuwa.


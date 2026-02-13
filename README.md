# User and Transactions  Relationship Visualization  System – Intern Task

A **Spring Boot + Neo4j** application that models users, transactions, and shared attributes as a **high-performance graph**. This system enables the discovery of complex fraud patterns that traditional relational databases often miss.

  
 

Designed as an **intern assignment**, this project is implemented with **good backend practices**, including a fully containerized environment.

  

  
---

  
 

### Key Features

    

* **Relationship Exploration:** Deep-dive into multi-hop entity connections.

  

* **Large-Scale Analysis:** Built to handle 100k+ transaction datasets.

  

* **Interactive Visualization:** Real-time graph rendering using **Cytoscape.js**.

  

  

---

  

  

## 🏗️ Architecture & Tech Stack

  

  

The system follows a decoupled architecture, ensuring the backend handles the heavy graph logic while the frontend focuses on rendering.

  

### Backend

  

* **Java 21** & **Spring Boot 3**

  

* **Spring Data Neo4j** (SDN)

  

* **Lombok** (Boilerplate reduction)

  
  

### Database

  

* **Neo4j 5** (Graph Database)

  

* **Cypher** (Query Language)

  

  

### Frontend & DevOps

  

* **Cytoscape.js** (Graph visualization library)

  

* **Docker & Docker Compose** (Containerization)

  

  

---

  

  

## 🛠️ Running the Project

  

  

### Prerequisites

  

* **Docker** and **Docker Compose** installed.

  

* *No local Java or Neo4j installation required.*

  

  

### Quick Start

  

1.  Clone the repository.

  

2.  Run the following command:

  

 

    `docker compose up --build`



    `✅ Neo4j is reachable` message in the terminal indicates that the application is ready to use

  

3.  **Access the Services:**

  

    * **Frontend UI:** `http://localhost:3000`

  

    * **Backend API:** `http://localhost:8080`

  

    * **Neo4j Browser:** `http://localhost:7475` (User: `neo4j`, Pass: `password`)

  

    Note: In the case of fresh re-run ,


        `docker compose down -v`

        `docker compose up --build`


---

---

  

  

## 🔌 API Documentation

  

  

### Users

  

* `POST /api/v1/users - Create a new user node.

  

* `GET /api/v1/users?page=0&size=50` - Paginated list with email/phone filters.

  

  

### Transactions

  

* `POST /api/v1/transactions` - Create a transaction and auto-link shared attributes.

  

* `GET /api/v1/transactions` - Advanced filtering and sorting.

  

    * **Filters:** `ip`, `deviceId`, `amountRange`, `status`, `paymentMethod`.

  

    * **Sorting:** `timestamp`, `amount`, `status`.

  

  

### Graph Visualization (Frontend Ready)

  

* `GET /api/v1/relationships/user/{id}` - Returns JSON structured for Cytoscape (Nodes/Edges).

  

* `GET /api/v1/relationships/transaction/{id}` - Returns the immediate neighborhood of a transaction.

  

  

### Data Pipeline

  

* `POST /api/v1/pipeline/execute` - Triggers the **100k record** generation.

  

* `POST /api/v1/pipeline/sample` - Loads a **deterministic demo** (5-10 users with pre-defined fraud links as defined in the specification doc).

  

* `DELETE /api/v1/pipeline/ - Purges the entire graph.

  

## 📈 Design Highlights

  

* **Graph-First Modeling:** Focuses on the density of connections rather than just row counts.

  

* **Selective Recomputation:** Relationships are intelligently updated to prevent performance degradation.

  

* **Visual-Ready APIs:** REST responses are mapped directly to Cytoscape.js object formats.

  

* **Production Readiness:** Uses Java 21 features and strictly containerized workflows.

  

  

---

  

  

## 🔮 Future Improvements

  

* **GDS Integration:** Use Neo4j Graph Data Science for community detection (Louvain/PageRank).

  

* **Real-time Scoring:** Implement a stream processor to score fraud as transactions arrive.

  

* **Auth:** Add Spring Security with JWT for protected API access.

  

  

---

  

  

**Author: Vishwas M Bharadwaj**

  

*Computer Science Engineering Student | Backend & Systems Enthusiast*
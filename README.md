# 🧠 Distributed AI RAG Agent (Akka Cluster + Spring AI)

A distributed, GPU-accelerated Question Answering system built with **Spring Boot** and **Akka Cluster**. This project implements a Retrieval-Augmented Generation (RAG) pipeline that processes documents and answers questions using **Llama 3.2** running locally on an Nvidia GPU.

## 🚀 Key Features

* **Distributed Architecture:** Uses **Akka Cluster** to distribute AI inference tasks across multiple nodes.
* **Actor Model:** Implements `RouterActor` (Load Balancing) and `LLMActor` (Worker) for concurrency.
* **GPU Acceleration:** Fully integrated with **Ollama (Llama 3.2)** running on Nvidia RTX 4050.
* **Vector Search:** Uses **Neo4j** as a Vector Store for semantic retrieval.
* **RAG Pipeline:** Supports PDF/Text ingestion, chunking, and context-aware generation.
* **Interactive UI:** Real-time streaming chat interface.

---

## 🏗️ System Architecture

**Message Flow:**

1. **User** sends a query via the Web UI (`/ask`).
2. **Spring Controller** forwards the request to the **Akka Actor System**.
3. **RouterActor** (Group Router) picks an available **LLMActor** node in the cluster.
4. **LLMActor** calls the `RagService`:
   * Retrieves relevant chunks from **Neo4j**.
   * Generates an answer using **Llama 3.2**.
5. **Result** is sent back to the user asynchronously.

---

## 🛠️ Prerequisites

* **Java 21+**
* **Maven**
* **Ollama** (Running Llama 3.2 model)
* **Neo4j** (Running on port 7687)

---

## 🚦 How to Run the Cluster

### 1. Start Node 1 (Seed Node & Web Server)

This node runs the Web UI (Port 8080) and the Akka Cluster Seed (Port 2551).
```bash
./mvnw spring-boot:run -DskipTests
```

**Access UI:** http://localhost:8080

### 2. Start Node 2 (Worker Node)

Open a new terminal. This node joins the existing cluster to share the workload. We override the ports to avoid conflicts on the same machine.
```bash
./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-Dakka.remote.artery.canonical.port=2552" -Dspring-boot.run.arguments="--server.port=8081"
```

**Verify Cluster:** Look for this log in Node 1's terminal:
```
Node [akka://RagCluster@127.0.0.1:2552] is JOINING
```

---

## 🧪 Testing the Load Balancer

1. Open Node 1's UI at http://localhost:8080.
2. Send multiple questions rapidly.
3. Check the terminal for Node 2. You will see:
```
   🤖 LLM Actor received task: [Your Question]
```

This confirms that the Akka Router is successfully distributing tasks to other nodes in the network.

---

## 📂 Project Structure

* `actors/` - Contains the Akka Typed actors (RouterActor, LLMActor).
* `config/` - Akka configuration beans (AkkaConfig).
* `RagService.java` - The RAG logic (Vector Store + Ollama Client).
* `resources/application.conf` - Akka Cluster networking configuration.

---

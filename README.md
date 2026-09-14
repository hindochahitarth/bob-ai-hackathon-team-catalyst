# Supply Chain Disruption Assistant & Cold Chain Monitor

> **Team Catalyst** — IBM Bob AI Hackathon

---

## Team

| Field | Value |
|---|---|
| **Team Name** | Team Catalyst |
| **Track** | AI |
| **Team Lead** | Hitarth Hindocha — hindochahitarth@gmail.com |
| **Members** | Harsh Vora, Atibali Saiyed, Bhakti Moteriya |

---

## Problem Statement

Supply chain disruptions — storms, port strikes, and geopolitical crises — cascade across hundreds of active shipments in ways that are impossible to track manually, leaving logistics teams to react only after delays have occurred. Cold-chain shipments such as vaccines and perishables are especially vulnerable: a single undetected temperature excursion can spoil cargo worth $500K or more, and today these breaches are only discovered at delivery, when it is too late to intervene.

---

## Solution

We built a system that automatically identifies which shipments are affected the moment a disruption occurs, recommends alternate routes using a **graph-based Dijkstra algorithm**, and continuously monitors cold-chain temperature sensor data to flag breaches by severity in real time. **IBM watsonx.ai (Granite)** transforms this flagged live data into a clear, plain-English AI Ops Brief so a supply chain manager instantly knows what is at risk and what to do next.

---

## Key Features

- **Disruption Impact Detection** — matches active disruptions against shipment routes to identify affected cargo
- **Graph-Based Route Optimization** — Dijkstra's algorithm across a logistics hub network for the best alternate route and carrier
- **Idle Fleet Asset Identification** — surfaces idle trucks available for redeployment
- **Cold Chain Breach Monitoring** — classifies temperature excursions by regulatory severity (minor/major)
- **Shipment Tracking Portal** — customers look up any shipment by tracking number
- **AI Ops Brief** — IBM watsonx.ai Granite generates a real-time natural-language operations summary from live data

---

## IBM watsonx.ai Integration

The AI Ops Brief feature (`/ops-brief`) is powered by **IBM watsonx.ai** using the **Granite** model.

### How it works

```
Live Supply-Chain Data  -->  OpsStats DTO  -->  WatsonxService
                                                      |
                                          IBM IAM (API key -> token)
                                                      |
                                     watsonx.ai /ml/v1/text/generation
                                                      |
                                          Granite model (Granite-3-8b-instruct)
                                                      |
                                          AI Ops Brief  -->  Ops Manager
```

### Setup (watsonx.ai credentials)

**Step 1 — Create a free IBM Cloud account**
1. Go to https://cloud.ibm.com/registration
2. Sign up with your email and verify
3. Log in to IBM Cloud

**Step 2 — Enable Watson Machine Learning**
1. In IBM Cloud, search for **"Watson Machine Learning"**
2. Select the **Lite (free)** plan and create the service
3. This gives you access to watsonx.ai

**Step 3 — Get your API key**
1. IBM Cloud top right: **Manage > Access (IAM) > API keys**
2. Click **Create an IBM Cloud API key**
3. Copy the key immediately (shown only once)

**Step 4 — Get your Project ID**
1. Go to https://dataplatform.cloud.ibm.com
2. Open or create a watsonx.ai project
3. Go to **Manage** tab > copy the **Project ID**

**Step 5 — Set environment variables**

```bash
# Windows PowerShell
$env:WATSONX_API_KEY     = "your-ibm-cloud-api-key"
$env:WATSONX_PROJECT_ID  = "your-watsonx-project-id"
$env:WATSONX_URL         = "https://us-south.ml.cloud.ibm.com"
$env:WATSONX_MODEL_ID    = "ibm/granite-3-8b-instruct"
```

Or set them in IntelliJ IDEA:
`Run > Edit Configurations > Environment Variables`

> The app works without these credentials — it falls back to a system-generated brief from live data with an "AI service unavailable" notice. No crashes.

---

## Tech Stack

| Category | Technologies |
|---|---|
| **Languages** | Java 17 |
| **Frameworks** | Spring Boot 3.3, Spring Data JPA, Thymeleaf, Spring WebFlux (WebClient) |
| **IBM Technologies** | IBM watsonx.ai, IBM Granite (granite-3-8b-instruct), IBM IAM |
| **Databases** | H2 (in-memory, auto-seeded) |
| **Algorithms** | Dijkstra shortest path (custom graph) |
| **Other** | Maven, Lombok |

---

## How to Run

```bash
# 1. Clone the repo
git clone https://github.com/hindochahitarth/bob-ai-hackathon-team-catalyst
cd bob-ai-hackathon-team-catalyst/src/backend/supplychain

# 2. (Optional) Set watsonx.ai credentials for live AI brief
# Windows PowerShell:
$env:WATSONX_API_KEY    = "your-key"
$env:WATSONX_PROJECT_ID = "your-project-id"
$env:WATSONX_MODEL_ID   = "ibm/granite-3-8b-instruct"

# 3. Run
.\mvnw.cmd spring-boot:run
```

App runs at: **http://localhost:8080**

> No external database setup needed — H2 auto-creates and seeds demo data on startup.

---

## Repository Structure

```
bob-ai-hackathon-team-catalyst/
├── src/backend/supplychain/       # Spring Boot application
│   ├── src/main/java/             # Java source
│   │   ├── controller/            # MVC controllers
│   │   ├── service/               # Business logic
│   │   │   ├── WatsonxService.java        # IBM watsonx.ai integration
│   │   │   ├── RouteOptimizationService.java  # Dijkstra algorithm
│   │   │   ├── DisruptionService.java
│   │   │   └── ColdChainService.java
│   │   └── model/                 # JPA entities + DTOs
│   └── src/main/resources/
│       ├── templates/             # Thymeleaf HTML pages
│       └── application.properties # Config (watsonx env vars)
├── docs/                          # Setup guide, architecture
├── demo/                          # Screenshots, video link
└── submission.yaml                # Hackathon submission metadata
```

---

## Demo

| Artifact | Link |
|---|---|
| Demo Video | [See demo/demo-video-link.txt](demo/demo-video-link.txt) |
| Screenshots | [See demo/screenshots/](demo/screenshots/) |
| Presentation | [See presentation/](presentation/) |

---

## Known Limitations

- Uses an in-memory H2 database with seeded sample data rather than live shipment/sensor feeds
- Route optimization weighs paths by distance only (no real-time cost/traffic)
- Not deployed to a live environment — demo shows app running locally

---

## What We Are Most Proud Of

The IBM watsonx.ai integration is genuinely load-bearing: it synthesizes disruption impacts, idle fleet data, and cold-chain breach severities from live application data into a single prioritized AI brief. We also implemented a real Dijkstra graph algorithm across an Indian logistics hub network for rerouting, rather than simple rule matching, and added a customer-facing tracking portal to make the system feel like a complete product.

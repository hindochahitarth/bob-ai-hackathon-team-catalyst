# 🚀 Supply Chain Disruption Assistant & Cold Chain Monitor

---

## 👥 Team

| Field | Value                                       |
|---|---------------------------------------------|
| **Team Name** | Team Catalyst                               |
| **Track** | AI                                          |
| **Team Lead** | Hitarth Hindocha — hindochahitarth@gmail.com |
| **Members** | Harsh Vora, Atibali Saiyed, Bhakti Moteriya |

---

## 🎯 Problem Statement

Supply chain disruptions — storms, port strikes, and geopolitical crises — cascade across hundreds of active shipments in ways that are impossible to track manually, leaving logistics teams to react only after delays have already occurred. Cold-chain shipments such as vaccines and perishables are especially vulnerable: a single undetected temperature excursion can spoil cargo worth $500K or more, and today these breaches are only discovered at delivery, when it's too late to intervene.

---

## 💡 Solution

We built a system that automatically identifies which shipments are affected the moment a disruption occurs, recommends alternate routes to avoid the impacted area, and continuously monitors cold-chain temperature sensor data to flag breaches by severity in real time. IBM Bob transforms this flagged data into a clear, plain-English operations brief, so a supply chain manager instantly knows what's at risk and what to do next — instead of discovering the problem after the damage is done.

---

## ✨ Key Features

- **Disruption Impact Detection:** Automatically matches active disruptions (storms, strikes) against shipment routes to identify affected cargo
- **Graph-Based Route Optimization:** Uses Dijkstra's algorithm across a route network to recommend the best alternate route or carrier when a segment is disrupted
- **Idle Fleet Asset Identification:** Surfaces idle trucks, containers, and vessels available for redeployment to overloaded routes
- **Cold Chain Breach Monitoring:** Analyzes real-time temperature sensor readings and classifies excursions by regulatory severity (minor/major)
- **Shipment Tracking Portal:** Lets customers and coordinators look up any shipment by tracking number to see live status, location, and cold chain readings
- **AI-Generated Ops Brief:** IBM Bob/watsonx.ai converts flagged data into a prioritized, natural-language summary for operations teams

---

## 🛠️ Tech Stack

| Category | Technologies |
|---|---|
| **Languages** | Java |
| **Frameworks** | Spring Boot, Spring Data JPA, Thymeleaf |
| **IBM Technologies** | IBM Bob, watsonx.ai |
| **Databases** | H2 (in-memory) |
| **Other** | Maven, GitHub Actions |

---

## 📁 Repository Structure

```
├── src/                  # All source code
├── docs/                 # Written documentation
│   ├── problem-statement.md
│   ├── solution-overview.md
│   ├── architecture.md
│   └── setup-guide.md
├── demo/                 # Demo artifacts
│   ├── screenshots/      # App screenshots
│   └── demo-video-link.txt  # Link to demo video
├── presentation/         # Slide deck
└── submission.yaml       # Structured submission metadata
```

---

## ⚡ How to Run

> **Copy these exact steps from your [`docs/setup-guide.md`](docs/setup-guide.md)**

```bash
# 1. Clone the repo
git clone https://github.com/[your-username]/bob-ai-hackathon-team-catalyst.git
cd bob-ai-hackathon-team-catalyst

# 2. Install dependencies
cd src/backend
mvn clean install

# 3. Configure environment
cp .env.example .env
# Edit .env with your watsonx.ai / IBM Bob API credentials

# 4. Run the project
mvn spring-boot:run
```

The app will be available at `http://localhost:8080`

---

## 🖥️ Demo

| Artifact | Link |
|---|---|
| 📹 Demo Video | [See demo/demo-video-link.txt](demo/demo-video-link.txt) |
| 🌐 Live Demo | [See demo/live-demo-url.txt](demo/live-demo-url.txt) |
| 🖼️ Screenshots | [See demo/screenshots/](demo/screenshots/) |
| 📊 Presentation | [See presentation/slides.pdf](presentation/) |

---

## ⚠️ Known Limitations

- Uses an in-memory H2 database with seeded sample data rather than live real-world shipment or sensor feeds
- The route optimization model currently weighs routes by distance only and does not yet factor in cost, carrier reliability, or real-time traffic
- Not yet deployed to a live environment — demo video shows the app running locally

---

## 🏅 What We're Most Proud Of
The IBM Bob integration is genuinely load-bearing: rather than just displaying raw flagged data, Bob synthesizes disruption impacts, idle fleet data, and cold-chain breach severities into a single prioritized action brief, mirroring how a real operations manager would want to triage a crisis in seconds rather than sifting through tables. We also went beyond the base requirements by implementing a real graph-based optimization model for rerouting and adding a customer-facing tracking portal to make the system feel like a complete product.
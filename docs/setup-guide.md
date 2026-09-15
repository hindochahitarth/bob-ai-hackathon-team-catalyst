# Setup Guide

This guide covers local development and the optional IBM watsonx.ai
configuration for Supply Chain Assistant.

## Prerequisites

Install the following:

- Java 17 or newer
- Git
- Internet access for Maven dependency downloads
- An IBM Cloud account with watsonx.ai access (optional)
- Ollama with a Granite model (optional local AI alternative)

Maven does not need to be installed separately because the repository includes
the Maven Wrapper (`mvnw` / `mvnw.cmd`).

## Clone the Repository

```bash
git clone https://github.com/hindochahitarth/bob-ai-hackathon-team-catalyst.git
cd bob-ai-hackathon-team-catalyst
```

The Spring Boot module is located at
`src/backend/supplychain`.

## Environment Variables

The application reads configuration from environment variables. No `.env`
file or external database is required.

| Variable | Description | Required |
|---|---|---|
| `WATSONX_API_KEY` | IBM Cloud API key used to request an IAM token | No |
| `WATSONX_PROJECT_ID` | watsonx.ai project ID | No |
| `WATSONX_URL` | watsonx.ai regional endpoint; defaults to `https://us-south.ml.cloud.ibm.com` | No |
| `WATSONX_MODEL_ID` | Granite model ID; defaults to `ibm/granite-3-8b-instruct` | No |
| `OLLAMA_URL` | Local Ollama endpoint; defaults to `http://localhost:11434` | No |
| `OLLAMA_MODEL` | Local model name; defaults to `granite3.1-dense:2b` | No |

The application works without AI credentials. The `/ops-brief` page returns a
system-generated brief from the live application data when watsonx.ai and
Ollama are unavailable.

### Windows PowerShell

```powershell
$env:WATSONX_API_KEY = "your-ibm-cloud-api-key"
$env:WATSONX_PROJECT_ID = "your-watsonx-project-id"
$env:WATSONX_URL = "https://us-south.ml.cloud.ibm.com"
$env:WATSONX_MODEL_ID = "ibm/granite-3-8b-instruct"
```

### macOS/Linux

```bash
export WATSONX_API_KEY="your-ibm-cloud-api-key"
export WATSONX_PROJECT_ID="your-watsonx-project-id"
export WATSONX_URL="https://us-south.ml.cloud.ibm.com"
export WATSONX_MODEL_ID="ibm/granite-3-8b-instruct"
```

Never commit real credentials.

## Run Locally

From the repository root:

### Windows

```powershell
cd src/backend/supplychain
.\mvnw.cmd spring-boot:run
```

### macOS/Linux

```bash
cd src/backend/supplychain
./mvnw spring-boot:run
```

Open <http://localhost:8080/>.

The application uses an in-memory H2 database. On the first run,
`DataSeeder` creates 6 routes, 18 shipments, 4 disruptions, 9 fleet assets,
and 21 temperature readings. No PostgreSQL, Docker, or migration command is
needed.

## Main Pages

| Page | URL | Purpose |
|---|---|---|
| Dashboard | `/dashboard` | Operational summary and recent activity |
| Disruptions | `/disruptions` | Active events and affected shipment analysis |
| Disruption impact | `/disruptions/{id}/impact` | Dijkstra reroute recommendations |
| Idle fleet | `/fleet/idle` | Assets currently marked `IDLE` |
| Cold chain | `/coldchain` | Latest readings and breach severity |
| Track shipment | `/track` | Search by tracking number |
| AI Ops Brief | `/ops-brief` | Generate a consolidated operations brief |

Sample tracking numbers include `TRK-00421`, `TRK-00430`, and `TRK-00436`.

## Run Tests

From `src/backend/supplychain`:

```powershell
.\mvnw.cmd test
```

```bash
./mvnw test
```

The test suite covers route optimization, watsonx.ai brief behavior, and
Spring application context startup.

## Optional: Run Local Granite with Ollama

Install Ollama, then download the configured model:

```bash
ollama pull granite3.1-dense:2b
ollama run granite3.1-dense:2b
```

Start the application with the default Ollama settings, or override them:

```powershell
$env:OLLAMA_URL = "http://localhost:11434"
$env:OLLAMA_MODEL = "granite3.1-dense:2b"
```

## Railway Deployment

The deployed demo is available at
<https://supplychain-catalyst-production.up.railway.app/>.

For a Railway service:

1. Connect the GitHub repository.
2. Set the service root directory to `src/backend/supplychain`, or configure
   the build and start commands to run from that directory.
3. Use `./mvnw spring-boot:run` for a simple demo deployment, or build with
   `./mvnw clean package` and start with
   `java -jar target/supplychain-0.0.1-SNAPSHOT.jar`.
4. Add the optional `WATSONX_*` variables in Railway if cloud AI generation
   is required.

## Troubleshooting

| Issue | Solution |
|---|---|
| `java` is not recognized | Install Java 17+ and ensure `JAVA_HOME` and `PATH` are configured |
| Maven wrapper permission denied on macOS/Linux | Run `chmod +x mvnw`, then retry `./mvnw test` |
| Port 8080 is already in use | Stop the other process or run with `--server.port=8081` |
| AI brief says service unavailable | Configure `WATSONX_API_KEY` and `WATSONX_PROJECT_ID`, or start Ollama; the system fallback is expected without either |
| watsonx.ai returns 401 | Verify the API key, project ID, regional URL, and model ID |
| No demo rows appear | H2 seeds only when route and shipment tables are empty; restart with a fresh in-memory application instance |

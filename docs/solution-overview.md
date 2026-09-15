# Solution Overview

## What We Built

Supply Chain Assistant is a browser-based operations console for monitoring
freight disruptions, shipment status, fleet availability, and cold-chain
conditions in one place. It turns a disruption such as an NH48 closure into a
list of affected shipments and a recommended bypass, instead of leaving a
dispatcher to compare route spreadsheets manually.

The live demo is available at
<https://supplychain-catalyst-production.up.railway.app/> and the source code
is available at
<https://github.com/hindochahitarth/bob-ai-hackathon-team-catalyst/>.

## How It Works

1. The dashboard loads operational counts from the seeded H2 data:
   shipments, disruptions, cold-chain shipments, and idle fleet assets.
2. The Disruptions page lists four events with their type, affected segment,
   description, and severity.
3. Selecting **Check Impact** matches the event's segment against each
   shipment route. For example, the NH48 event can affect routes containing
   `NH48`, while a Mumbai Port event blocks the `MUMBAI_PORT` node.
4. The route optimizer applies Dijkstra's shortest-path algorithm to the
   logistics graph, excluding the blocked segment or node, and recommends an
   available carrier suited to the cargo.
5. A dispatcher can approve a successful recommendation. The shipment is
   updated to `IN_TRANSIT (REROUTED)` and records the bypass location.
6. The Idle Fleet page shows the 5 seeded assets currently marked `IDLE`,
   including trucks, a container, and a vessel.
7. The Cold Chain page checks the latest reading for 7 cold-chain shipments
   against the 2–8 °C target. It identifies normal readings plus minor and
   major breaches.
8. The AI Ops Brief aggregates those results and asks IBM Granite for a
   concise operational summary. If watsonx credentials are not configured,
   the application returns a system-generated brief instead of failing.
9. The Track Shipment page lets a user look up sample numbers such as
   `TRK-00421` or `TRK-00436` and view cargo, route, status, and location.

## Architecture Diagram

> See [`architecture.md`](architecture.md) for the detailed architecture and
> seeded data model.

```text
[Browser]
    |
    v
[Spring Boot MVC + Thymeleaf]
    |
    +--> [Spring Data JPA] --> [H2 seeded demo data]
    |
    +--> [DisruptionService] --> [Affected shipments]
    |                              |
    |                              v
    |                       [Dijkstra reroute graph]
    |
    +--> [ColdChainService] --> [Latest reading + breach status]
    |
    +--> [WatsonxService] --> [IBM Granite / system fallback]
```

## Key Design Decisions

| Decision | Rationale |
|---|---|
| Server-rendered Spring MVC with Thymeleaf | Keeps the hackathon deployment simple while providing complete pages without a separate frontend build or API gateway |
| H2 with deterministic seed data | Makes the demo reproducible and runnable without external database setup; the limitation is documented for production migration |
| Segment-based disruption matching | Directly connects a disruption such as `NH48` or `MUMBAI_PORT` to route data and makes impact analysis explainable |
| Dijkstra shortest-path optimization | Calculates a real alternate path over the logistics graph rather than returning a hard-coded recommendation |
| Latest-reading cold-chain classification | Represents the actionable current state and applies explicit 2–8 °C thresholds |
| AI brief with system fallback | Keeps `/ops-brief` usable without credentials while still integrating IBM Granite when configured |

## IBM Technologies Used

- **IBM watsonx.ai:** `WatsonxService` requests a bearer token from IBM IAM
  and sends the aggregated `OpsStats` prompt to the configured Granite text
  generation endpoint.
- **IBM Granite:** Generates the natural-language AI Operations Brief that
  summarizes active shipments, disruption impact, idle assets, and cold-chain
  alerts.
- **IBM IAM:** Exchanges the environment-provided IBM Cloud API key for the
  short-lived access token used by watsonx.ai.

The integration is configured with `WATSONX_API_KEY`,
`WATSONX_PROJECT_ID`, `WATSONX_URL`, and `WATSONX_MODEL_ID`. No real
credentials are included in the repository.

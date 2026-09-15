# Problem Statement

## Background

Indian supply chains depend on interconnected road, port, airport, and warehouse
corridors. A disruption at one segment can affect many shipments across the
network. The operational response is even more time-sensitive for
cold-chain cargo such as pharmaceuticals, vaccines, and perishables, where a
temperature excursion can make a shipment unusable.

## The Problem

Operations teams need to answer four questions quickly:

1. Which active shipments use the disrupted route segment?
2. Which alternate route and carrier can move each affected shipment?
3. Which fleet assets are available for immediate redeployment?
4. Which cold-chain shipments are outside the safe 2–8 °C range?

Without a consolidated view, these decisions require manually comparing route
plans, shipment records, fleet availability, and sensor readings. The result is
delayed intervention, avoidable rerouting costs, and a higher risk of cargo
loss.

## Who Is Affected

The primary user is a supply-chain operations manager or dispatcher who
coordinates active freight across multiple Indian logistics corridors. The
secondary user is a customer or service representative who needs to check a
shipment by tracking number.

## Why It Matters

The demo network contains 6 routes, 18 shipments, 4 active disruption events,
9 fleet assets, and 7 cold-chain shipments. A single event such as the seeded
NH48 monsoon disruption or Mumbai Port strike can affect shipments on more
than one route. The cold-chain sample also includes minor and major breaches,
including readings of 8.9 °C, 1.4 °C, and 14.8 °C.

The cost of missing an event is not limited to delay: it can include spoiled
temperature-sensitive cargo, missed delivery commitments, and inefficient use
of scarce vehicles and containers.

## Why Existing Solutions Fall Short

Shipment tracking, route planning, fleet allocation, and temperature
monitoring are often separate workflows. A status page may show that a
shipment is delayed, but not why it is affected, which bypass is shortest, or
whether a suitable idle asset is available. This project brings those
decisions together in one operations dashboard and produces a prioritized
AI Operations Brief from the same underlying data.

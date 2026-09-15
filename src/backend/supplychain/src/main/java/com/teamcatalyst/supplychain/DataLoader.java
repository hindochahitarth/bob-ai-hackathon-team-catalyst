package com.teamcatalyst.supplychain;

import com.teamcatalyst.supplychain.model.*;
import com.teamcatalyst.supplychain.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Seeds demo data into the H2 in-memory database on every startup.
 * Skips seeding if data already exists (idempotent).
 */
@Component
@RequiredArgsConstructor
public class DataLoader implements ApplicationRunner {

    private final RouteRepository           routeRepo;
    private final ShipmentRepository        shipmentRepo;
    private final TempReadingRepository     tempReadingRepo;
    private final FleetAssetRepository      fleetAssetRepo;
    private final DisruptionEventRepository disruptionRepo;

    @Override
    public void run(ApplicationArguments args) {
        if (shipmentRepo.count() > 0) {
            return; // already seeded
        }

        // ── Routes ──────────────────────────────────────────────────────────
        Route r1 = routeRepo.save(new Route(null, "Mumbai", "Delhi",
                "Mumbai|Pune|Nashik|Aurangabad|Nagpur|Bhopal|Delhi", 1450.0, "FastTrack Logistics"));
        Route r2 = routeRepo.save(new Route(null, "Chennai", "Bangalore",
                "Chennai|Vellore|Krishnagiri|Bangalore", 350.0, "SouthExpress Carriers"));
        Route r3 = routeRepo.save(new Route(null, "Kolkata", "Hyderabad",
                "Kolkata|Bhubaneswar|Visakhapatnam|Hyderabad", 1200.0, "EastWest Freight"));
        Route r4 = routeRepo.save(new Route(null, "Delhi", "Jaipur",
                "Delhi|Gurgaon|Alwar|Jaipur", 280.0, "RajExpress"));
        Route r5 = routeRepo.save(new Route(null, "Ahmedabad", "Mumbai",
                "Ahmedabad|Vadodara|Surat|Mumbai", 530.0, "WestCoast Haul"));

        // ── Cold-chain Shipments (with owner contacts) ────────────────────
        Shipment s1 = shipmentRepo.save(new Shipment(null,
                "TRK-00421", "Pharmaceuticals", true, r1,
                "In Transit", "Bhopal",
                "Dr. Ananya Sharma", "ananya.sharma@pharmalogix.in", "+91-98201-11234"));

        Shipment s2 = shipmentRepo.save(new Shipment(null,
                "TRK-00587", "Vaccines", true, r2,
                "In Transit", "Krishnagiri",
                "Rajiv Menon", "rajiv.menon@coldmedix.in", "+91-94400-55678"));

        Shipment s3 = shipmentRepo.save(new Shipment(null,
                "TRK-00634", "Dairy Products", true, r5,
                "Delayed", "Surat",
                "Priya Desai", "priya.desai@dairyfresh.co.in", "+91-79001-88321"));

        Shipment s4 = shipmentRepo.save(new Shipment(null,
                "TRK-00712", "Frozen Seafood", true, r3,
                "In Transit", "Visakhapatnam",
                "Suresh Nair", "suresh.nair@aquafreeze.in", "+91-40056-77432"));

        Shipment s5 = shipmentRepo.save(new Shipment(null,
                "TRK-00855", "Biological Samples", true, r4,
                "Delayed", "Alwar",
                "Dr. Kavya Iyer", "kavya.iyer@biochain.org", "+91-11200-93210"));

        // ── Non-cold-chain Shipments ─────────────────────────────────────
        shipmentRepo.save(new Shipment(null,
                "TRK-00301", "Textiles", false, r1,
                "In Transit", "Nagpur",
                null, null, null));

        shipmentRepo.save(new Shipment(null,
                "TRK-00388", "Auto Parts", false, r3,
                "Delivered", "Hyderabad",
                null, null, null));

        // ── Temperature Readings ─────────────────────────────────────────
        LocalDateTime now = LocalDateTime.now();

        // TRK-00421 — Normal (5.2 °C)
        tempReadingRepo.save(new TempReading(null, s1, 5.2, now.minusHours(1)));
        tempReadingRepo.save(new TempReading(null, s1, 4.8, now.minusHours(3)));

        // TRK-00587 — Minor Breach (9.1 °C — slightly above 8 °C ceiling)
        tempReadingRepo.save(new TempReading(null, s2, 9.1, now.minusMinutes(45)));
        tempReadingRepo.save(new TempReading(null, s2, 7.5, now.minusHours(2)));

        // TRK-00634 — Major Breach (14.3 °C — well above 12 °C threshold)
        tempReadingRepo.save(new TempReading(null, s3, 14.3, now.minusMinutes(20)));
        tempReadingRepo.save(new TempReading(null, s3, 11.8, now.minusHours(1)));

        // TRK-00712 — Normal (3.0 °C)
        tempReadingRepo.save(new TempReading(null, s4, 3.0, now.minusMinutes(30)));

        // TRK-00855 — Major Breach (-4.5 °C — below -2 °C floor)
        tempReadingRepo.save(new TempReading(null, s5, -4.5, now.minusMinutes(10)));
        tempReadingRepo.save(new TempReading(null, s5, -3.1, now.minusHours(1)));

        // ── Fleet Assets ──────────────────────────────────────────────────
        fleetAssetRepo.save(new FleetAsset(null, "Heavy Truck", "TRK-C22",  "MAINTENANCE", "Chennai"));
        fleetAssetRepo.save(new FleetAsset(null, "Heavy Truck", "TRK-D01",  "IDLE",        "Delhi"));
        fleetAssetRepo.save(new FleetAsset(null, "Heavy Truck", "TRK-D02",  "IDLE",        "Delhi"));
        fleetAssetRepo.save(new FleetAsset(null, "Heavy Truck", "TRK-D03",  "IDLE",        "Delhi"));
        fleetAssetRepo.save(new FleetAsset(null, "Van",         "VAN-M01",  "IDLE",        "Mumbai"));
        fleetAssetRepo.save(new FleetAsset(null, "Van",         "VAN-M02",  "IDLE",        "Mumbai"));
        fleetAssetRepo.save(new FleetAsset(null, "Reefer Truck","REEF-B01", "ACTIVE",      "Bangalore"));
        fleetAssetRepo.save(new FleetAsset(null, "Reefer Truck","REEF-B02", "ACTIVE",      "Pune"));

        // ── Disruption Events ─────────────────────────────────────────────
        disruptionRepo.save(new DisruptionEvent(null,
                "Port Congestion", "Mumbai|Pune",
                "JNPT Mumbai facing severe congestion; vessel berthing delayed by 48h.", "HIGH"));
        disruptionRepo.save(new DisruptionEvent(null,
                "Road Closure", "Delhi|Gurgaon|Alwar",
                "NH-48 closed between Delhi and Alwar due to accident. Single-lane diversion active.", "HIGH"));
        disruptionRepo.save(new DisruptionEvent(null,
                "Weather", "Chennai|Vellore",
                "Heavy rainfall on Chennai–Vellore stretch. Speed restrictions imposed.", "MEDIUM"));
        disruptionRepo.save(new DisruptionEvent(null,
                "Strike", "Kolkata|Bhubaneswar",
                "Transporter strike near Kolkata affecting outbound freight. Expected resolution: 24h.", "MEDIUM"));
        disruptionRepo.save(new DisruptionEvent(null,
                "Maintenance", "Nagpur|Bhopal",
                "Planned road maintenance on Nagpur–Bhopal section. Minimal delay expected.", "LOW"));
    }
}

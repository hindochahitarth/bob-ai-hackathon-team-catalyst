package com.teamcatalyst.supplychain.config;

import com.teamcatalyst.supplychain.model.DisruptionEvent;
import com.teamcatalyst.supplychain.model.FleetAsset;
import com.teamcatalyst.supplychain.model.Route;
import com.teamcatalyst.supplychain.model.Shipment;
import com.teamcatalyst.supplychain.model.TempReading;
import com.teamcatalyst.supplychain.repository.DisruptionEventRepository;
import com.teamcatalyst.supplychain.repository.FleetAssetRepository;
import com.teamcatalyst.supplychain.repository.RouteRepository;
import com.teamcatalyst.supplychain.repository.ShipmentRepository;
import com.teamcatalyst.supplychain.repository.TempReadingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final RouteRepository routeRepository;
    private final ShipmentRepository shipmentRepository;
    private final DisruptionEventRepository disruptionEventRepository;
    private final FleetAssetRepository fleetAssetRepository;
    private final TempReadingRepository tempReadingRepository;

    @Override
    public void run(String... args) {
        if (routeRepository.count() > 0 || shipmentRepository.count() > 0) {
            log.info("Database already contains data — skipping seed execution.");
            return;
        }

        log.info("Seeding initial supply chain demo data...");

        // 1. Seed Routes (6 Indian corridors)
        Route r1 = new Route(null, "Mumbai", "Delhi", "MUMBAI_PORT,NH48,SURAT_HUB,JAIPUR_CORRIDOR,DELHI_HUB", 1420.0, "Blue Dart Express");
        Route r2 = new Route(null, "Chennai", "Pune", "CHENNAI_PORT,NH48,BANGALORE_RING,PUNE_DEPOT", 1190.0, "TCI Freight");
        Route r3 = new Route(null, "Surat", "Bangalore", "SURAT_HUB,NH48,MUMBAI_BYPASS,PUNE_DEPOT,BANGALORE_RING", 1250.0, "Delhivery Logistics");
        Route r4 = new Route(null, "Kolkata", "Mumbai", "KOLKATA_PORT,NH53,NAGPUR_CROSSING,NASHIK_HUB,MUMBAI_PORT", 1960.0, "Gati-KWE");
        Route r5 = new Route(null, "Delhi", "Bangalore", "DELHI_HUB,NH44,GWALIOR,NAGPUR_CROSSING,HYDERABAD_HUB,BANGALORE_RING", 2150.0, "VRL Logistics");
        Route r6 = new Route(null, "Ahmedabad", "Kochi", "AHMEDABAD_AIRPORT,NH48,MUMBAI_BYPASS,GOA_COASTAL,KOCHI_PORT", 1780.0, "Safexpress");

        List<Route> routes = routeRepository.saveAll(List.of(r1, r2, r3, r4, r5, r6));
        r1 = routes.get(0);
        r2 = routes.get(1);
        r3 = routes.get(2);
        r4 = routes.get(3);
        r5 = routes.get(4);
        r6 = routes.get(5);

        // 2. Seed Disruption Events (4 realistic disruptions matching route segments)
        DisruptionEvent d1 = new DisruptionEvent(null, "WEATHER", "NH48",
                "Severe monsoon flooding and rockslides along Western Ghats section causing standstill traffic.", "HIGH");
        DisruptionEvent d2 = new DisruptionEvent(null, "PORT_STRIKE", "MUMBAI_PORT",
                "Dock workers union 48-hour flash strike stalling inbound and outbound container movement.", "HIGH");
        DisruptionEvent d3 = new DisruptionEvent(null, "GEOPOLITICAL", "DELHI_HUB",
                "Regional transport strike and farmer toll blockade restricting commercial vehicle movements.", "MEDIUM");
        DisruptionEvent d4 = new DisruptionEvent(null, "WEATHER", "NAGPUR_CROSSING",
                "Dense winter fog causing reduced visibility (<50m) and mandatory slow transit speeds.", "LOW");

        disruptionEventRepository.saveAll(List.of(d1, d2, d3, d4));

        // 3. Seed Shipments (18 shipments across routes)
        Shipment s1  = new Shipment(null, "TRK-00421", "Pharmaceuticals", true,  r1, "IN_TRANSIT", "Surat Hub",        "Dr. Ananya Sharma", "ananya.sharma@pharmalogix.in",  "+91-98201-11234");
        Shipment s2  = new Shipment(null, "TRK-00422", "Electronics",     false, r1, "DELAYED",    "NH48 Vadodara",    null, null, null);
        Shipment s3  = new Shipment(null, "TRK-00423", "Textiles",        false, r1, "DELIVERED",  "Delhi Hub",        null, null, null);
        Shipment s4  = new Shipment(null, "TRK-00424", "Automotive Parts",false, r2, "IN_TRANSIT", "Bangalore Ring",   null, null, null);
        Shipment s5  = new Shipment(null, "TRK-00425", "Perishables",     true,  r2, "IN_TRANSIT", "Hosur",            "Meena Krishnan",     "meena.krishnan@freshhaul.in",   "+91-44100-22345");
        Shipment s6  = new Shipment(null, "TRK-00426", "Electronics",     false, r2, "DELAYED",    "NH48 Near Pune",   null, null, null);
        Shipment s7  = new Shipment(null, "TRK-00427", "Pharmaceuticals", true,  r3, "DELAYED",    "Pune Depot",       "Vikram Joshi",       "vikram.joshi@medcargo.in",      "+91-20900-44567");
        Shipment s8  = new Shipment(null, "TRK-00428", "Textiles",        false, r3, "IN_TRANSIT", "Mumbai Bypass",    null, null, null);
        Shipment s9  = new Shipment(null, "TRK-00429", "Electronics",     false, r3, "DELIVERED",  "Bangalore Ring",   null, null, null);
        Shipment s10 = new Shipment(null, "TRK-00430", "Perishables",     true,  r4, "IN_TRANSIT", "Nagpur Crossing",  "Sonal Mehta",        "sonal.mehta@coldhub.in",        "+91-71200-66789");
        Shipment s11 = new Shipment(null, "TRK-00431", "Textiles",        false, r4, "IN_TRANSIT", "Nashik Hub",       null, null, null);
        Shipment s12 = new Shipment(null, "TRK-00432", "Heavy Machinery", false, r4, "DELAYED",    "Kolkata Port",     null, null, null);
        Shipment s13 = new Shipment(null, "TRK-00433", "Pharmaceuticals", true,  r5, "IN_TRANSIT", "Gwalior",          "Dr. Pradeep Nair",   "pradeep.nair@lifesciences.in",  "+91-11300-55890");
        Shipment s14 = new Shipment(null, "TRK-00434", "Electronics",     false, r5, "IN_TRANSIT", "Hyderabad Hub",    null, null, null);
        Shipment s15 = new Shipment(null, "TRK-00435", "FMCG",            false, r5, "DELIVERED",  "Bangalore Ring",   null, null, null);
        Shipment s16 = new Shipment(null, "TRK-00436", "Vaccines",        true,  r6, "DELAYED",    "Goa Coastal",      "Kavitha Pillai",     "kavitha.pillai@vaccinex.in",    "+91-48400-33901");
        Shipment s17 = new Shipment(null, "TRK-00437", "Fresh Seafood",   true,  r6, "IN_TRANSIT", "Mumbai Bypass",    "Arjun Shetty",       "arjun.shetty@seafreshtrans.in", "+91-22500-11456");
        Shipment s18 = new Shipment(null, "TRK-00438", "Textiles",        false, r6, "DELIVERED",  "Kochi Port",       null, null, null);

        List<Shipment> shipments = shipmentRepository.saveAll(List.of(
                s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12, s13, s14, s15, s16, s17, s18
        ));
        s1  = shipments.get(0);
        s5  = shipments.get(4);
        s7  = shipments.get(6);
        s10 = shipments.get(9);
        s13 = shipments.get(12);
        s16 = shipments.get(15);
        s17 = shipments.get(16);

        // 4. Seed Fleet Assets (9 assets: TRUCK, CONTAINER, VESSEL; IDLE vs IN_USE)
        FleetAsset a1 = new FleetAsset(null, "TRUCK",     "TRK-MH01-4412", "IDLE",   "Mumbai Hub");
        FleetAsset a2 = new FleetAsset(null, "TRUCK",     "TRK-DL04-9810", "IN_USE", "Delhi Hub");
        FleetAsset a3 = new FleetAsset(null, "TRUCK",     "TRK-KA02-7721", "IDLE",   "Bangalore Depot");
        FleetAsset a4 = new FleetAsset(null, "CONTAINER", "CNT-IN-2041",   "IN_USE", "Chennai Port");
        FleetAsset a5 = new FleetAsset(null, "CONTAINER", "CNT-IN-8890",   "IDLE",   "Surat Warehouse");
        FleetAsset a6 = new FleetAsset(null, "CONTAINER", "CNT-IN-5512",   "IN_USE", "Kolkata Port");
        FleetAsset a7 = new FleetAsset(null, "VESSEL",    "VES-MAH-001",   "IN_USE", "JNPT Terminal");
        FleetAsset a8 = new FleetAsset(null, "VESSEL",    "VES-KOC-009",   "IDLE",   "Kochi Anchorage");
        FleetAsset a9 = new FleetAsset(null, "TRUCK",     "TRK-TN09-3320", "IDLE",   "Pune Logistics Park");

        fleetAssetRepository.saveAll(List.of(a1, a2, a3, a4, a5, a6, a7, a8, a9));

        // 5. Seed Temperature Readings for Cold-Chain Shipments (7 cold-chain shipments)
        // 2-3 readings per cold-chain shipment, timestamps a few hours apart
        LocalDateTime now = LocalDateTime.now();

        // s1 (TRK-00421): Normal (latest 5.1°C)
        TempReading t1_1 = new TempReading(null, s1, 4.5, now.minusHours(6));
        TempReading t1_2 = new TempReading(null, s1, 4.8, now.minusHours(3));
        TempReading t1_3 = new TempReading(null, s1, 5.1, now.minusHours(1));

        // s5 (TRK-00425): Normal (latest 4.0°C)
        TempReading t5_1 = new TempReading(null, s5, 3.8, now.minusHours(5));
        TempReading t5_2 = new TempReading(null, s5, 4.1, now.minusHours(2));
        TempReading t5_3 = new TempReading(null, s5, 4.0, now.minusHours(1));

        // s7 (TRK-00427): Minor Breach (latest 8.9°C)
        TempReading t7_1 = new TempReading(null, s7, 5.2, now.minusHours(6));
        TempReading t7_2 = new TempReading(null, s7, 7.8, now.minusHours(3));
        TempReading t7_3 = new TempReading(null, s7, 8.9, now.minusHours(1));

        // s10 (TRK-00430): Minor Breach (latest 1.4°C - below 2°C)
        TempReading t10_1 = new TempReading(null, s10, 4.0, now.minusHours(7));
        TempReading t10_2 = new TempReading(null, s10, 3.2, now.minusHours(4));
        TempReading t10_3 = new TempReading(null, s10, 1.4, now.minusHours(1));

        // s13 (TRK-00433): Normal (latest 5.2°C)
        TempReading t13_1 = new TempReading(null, s13, 5.0, now.minusHours(6));
        TempReading t13_2 = new TempReading(null, s13, 5.5, now.minusHours(3));
        TempReading t13_3 = new TempReading(null, s13, 5.2, now.minusHours(1));

        // s16 (TRK-00436): Major Breach (latest 14.8°C - significantly above 8°C / above 10°C)
        TempReading t16_1 = new TempReading(null, s16, 6.0, now.minusHours(6));
        TempReading t16_2 = new TempReading(null, s16, 9.5, now.minusHours(3));
        TempReading t16_3 = new TempReading(null, s16, 14.8, now.minusHours(1));

        // s17 (TRK-00437): Normal (latest 3.6°C)
        TempReading t17_1 = new TempReading(null, s17, 3.5, now.minusHours(5));
        TempReading t17_2 = new TempReading(null, s17, 3.8, now.minusHours(2));
        TempReading t17_3 = new TempReading(null, s17, 3.6, now.minusHours(1));

        tempReadingRepository.saveAll(List.of(
                t1_1, t1_2, t1_3,
                t5_1, t5_2, t5_3,
                t7_1, t7_2, t7_3,
                t10_1, t10_2, t10_3,
                t13_1, t13_2, t13_3,
                t16_1, t16_2, t16_3,
                t17_1, t17_2, t17_3
        ));

        log.info("Data seeding complete! Seeded: 6 Routes, 4 Disruptions, 18 Shipments, 9 FleetAssets, 21 TempReadings.");
    }
}

package com.teamcatalyst.supplychain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamcatalyst.supplychain.model.OpsStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WatsonxServiceTest {

    private WatsonxService service;

    private OpsStats sampleStats() {
        return OpsStats.builder()
                .activeShipments(248)
                .disruptedShipments(32)
                .activeDisruptions(4)
                .idleFleetAssets(14)
                .coldChainAlerts(7)
                .criticalColdChainAlerts(2)
                .majorDisruptionSummaries(List.of("Road closure on NH-48 [HIGH]"))
                .build();
    }

    @BeforeEach
    void setUp() {
        service = new WatsonxService(WebClient.builder(), new ObjectMapper());
    }

    // Test 1: Missing config returns fallback without exception
    @Test
    void missingConfig_returnsFallback() {
        WatsonxService.BriefResult result = service.generateOpsBrief(sampleStats());
        assertThat(result.aiGenerated()).isFalse();
        assertThat(result.text()).isNotBlank();
        assertThat(result.text()).doesNotContain("null");
    }

    // Test 2: Fallback brief contains real stat numbers
    @Test
    void fallbackBrief_containsActualStatNumbers() {
        WatsonxService.BriefResult result = service.generateOpsBrief(sampleStats());
        assertThat(result.text()).contains("248");
        assertThat(result.text()).contains("32");
        assertThat(result.text()).contains("14");
        assertThat(result.text()).contains("7");
        assertThat(result.text()).contains("2");
    }

    // Test 3: Fallback with zero alerts does not mention them
    @Test
    void fallbackBrief_noAlerts_doesNotMentionAlerts() {
        OpsStats noAlerts = OpsStats.builder()
                .activeShipments(100).disruptedShipments(0).activeDisruptions(0)
                .idleFleetAssets(0).coldChainAlerts(0).criticalColdChainAlerts(0)
                .majorDisruptionSummaries(List.of()).build();
        WatsonxService.BriefResult result = service.generateOpsBrief(noAlerts);
        assertThat(result.text()).doesNotContain("cold-chain alerts");
        assertThat(result.text()).doesNotContain("disrupted shipments");
    }

    // Test 4: BriefResult AI flag is correct
    @Test
    void briefResult_aiFlagIsCorrect() {
        WatsonxService.BriefResult ai       = WatsonxService.BriefResult.ai("AI text", "IBM Granite (local)");
        WatsonxService.BriefResult fallback = WatsonxService.BriefResult.fallback("Fallback text");
        assertThat(ai.aiGenerated()).isTrue();
        assertThat(ai.text()).isEqualTo("AI text");
        assertThat(fallback.aiGenerated()).isFalse();
        assertThat(fallback.text()).isEqualTo("Fallback text");
    }

    // Test 5: Prompt contains all stat values
    @Test
    void promptContainsCurrentStats() throws Exception {
        OpsStats stats = sampleStats();
        String prompt = service.buildPrompt(stats);
        assertThat(prompt).contains("248");
        assertThat(prompt).contains("32");
        assertThat(prompt).contains("4");
        assertThat(prompt).contains("14");
        assertThat(prompt).contains("7");
        assertThat(prompt).contains("2");
        assertThat(prompt).contains("NH-48");
    }

    // Test 6: Fallback on missing idle/disruptions - correct minimal text
    @Test
    void fallbackBrief_onlyActiveShipments_showsCount() {
        OpsStats minimal = OpsStats.builder()
                .activeShipments(50).disruptedShipments(0).activeDisruptions(0)
                .idleFleetAssets(0).coldChainAlerts(0).criticalColdChainAlerts(0)
                .majorDisruptionSummaries(List.of()).build();
        WatsonxService.BriefResult result = service.generateOpsBrief(minimal);
        assertThat(result.text()).contains("50");
        assertThat(result.aiGenerated()).isFalse();
    }
}
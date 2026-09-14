package com.teamcatalyst.supplychain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamcatalyst.supplychain.model.OpsStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * WatsonxService - handles IBM watsonx.ai integration.
 *
 * 1. IBM IAM token acquisition and caching.
 * 2. Prompt construction from OpsStats.
 * 3. watsonx.ai /ml/v1/text/generation REST call.
 * 4. Graceful fallback when AI is unavailable.
 *
 * API keys and tokens are NEVER logged.
 */
@Slf4j
@Service
public class WatsonxService {

    @Value("${watsonx.api-key:}")
    private String apiKey;

    @Value("${watsonx.project-id:}")
    private String projectId;

    @Value("${watsonx.url:https://us-south.ml.cloud.ibm.com}")
    private String watsonxUrl;

    @Value("${watsonx.model-id:ibm/granite-3-8b-instruct}")
    private String modelId;

    private static final String IAM_URL = "https://iam.cloud.ibm.com/identity/token";
    private static final long TOKEN_REFRESH_BUFFER_SECONDS = 300;

    private String cachedToken;
    private Instant tokenExpiresAt;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public WatsonxService(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public BriefResult generateOpsBrief(OpsStats stats) {
        if (!isConfigured()) {
            log.warn("watsonx.ai not configured (missing API key or project ID); using fallback Ops Brief");
            return BriefResult.fallback(buildFallbackBrief(stats));
        }
        try {
            log.info("Generating AI Ops Brief via watsonx.ai (model: {})", modelId);
            String token = getOrRefreshToken();
            String prompt = buildPrompt(stats);
            String aiText = callWatsonxGeneration(token, prompt);
            log.info("watsonx.ai Ops Brief generated successfully");
            return BriefResult.ai(aiText);
        } catch (Exception ex) {
            log.warn("watsonx.ai unavailable; using fallback Ops Brief. Reason: {}", ex.getMessage());
            return BriefResult.fallback(buildFallbackBrief(stats));
        }
    }

    // ── IAM Authentication ────────────────────────────────────────────────────

    private String getOrRefreshToken() throws Exception {
        if (cachedToken != null && tokenExpiresAt != null
                && Instant.now().isBefore(tokenExpiresAt.minusSeconds(TOKEN_REFRESH_BUFFER_SECONDS))) {
            return cachedToken;
        }
        return fetchNewToken();
    }

    private String fetchNewToken() throws Exception {
        log.info("Requesting new IBM IAM access token");
        String body = "grant_type=urn%3Aibm%3Aparams%3Aoauth%3Agrant-type%3Aapikey&apikey=" + apiKey;
        String responseBody = webClient.post()
                .uri(IAM_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JsonNode json = objectMapper.readTree(responseBody);
        if (!json.has("access_token")) {
            throw new IllegalStateException("IAM response missing access_token");
        }
        cachedToken = json.get("access_token").asText();
        long expiresIn = json.has("expires_in") ? json.get("expires_in").asLong() : 3600L;
        tokenExpiresAt = Instant.now().plusSeconds(expiresIn);
        log.info("IAM token obtained; valid for {} seconds", expiresIn);
        return cachedToken;
    }

    // ── Prompt Construction ───────────────────────────────────────────────────

    String buildPrompt(OpsStats stats) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a senior supply-chain operations analyst.\n");
        sb.append("Generate a concise daily operations brief based ONLY on the data below.\n");
        sb.append("Do NOT invent shipments, disruptions, or events.\n\n");
        sb.append("CURRENT OPERATIONAL DATA:\n");
        sb.append("- Active shipments: ").append(stats.getActiveShipments()).append("\n");
        sb.append("- Disrupted shipments: ").append(stats.getDisruptedShipments()).append("\n");
        sb.append("- Active disruptions: ").append(stats.getActiveDisruptions()).append("\n");
        sb.append("- Idle fleet assets: ").append(stats.getIdleFleetAssets()).append("\n");
        sb.append("- Cold-chain alerts: ").append(stats.getColdChainAlerts()).append("\n");
        sb.append("- Critical cold-chain alerts: ").append(stats.getCriticalColdChainAlerts()).append("\n");

        List<String> majors = stats.getMajorDisruptionSummaries();
        if (majors != null && !majors.isEmpty()) {
            sb.append("\nMAJOR ACTIVE DISRUPTIONS:\n");
            for (String d : majors) {
                sb.append("  - ").append(d).append("\n");
            }
        }
        sb.append("\nProvide a structured brief with sections:\n");
        sb.append("1. Overall Situation\n2. Major Disruptions\n");
        sb.append("3. Immediate Actions\n4. Cold-Chain Risks\n5. Fleet Priorities\n\n");
        sb.append("Keep it concise, professional, max 300 words.\nBrief:\n");
        return sb.toString();
    }

    // ── watsonx.ai REST Call ──────────────────────────────────────────────────

    private String callWatsonxGeneration(String token, String prompt) throws Exception {
        String url = watsonxUrl + "/ml/v1/text/generation?version=2023-05-29";
        Map<String, Object> requestBody = Map.of(
                "model_id", modelId,
                "project_id", projectId,
                "input", prompt,
                "parameters", Map.of(
                        "decoding_method", "greedy",
                        "max_new_tokens", 512,
                        "min_new_tokens", 50,
                        "repetition_penalty", 1.1
                )
        );
        String responseBody;
        try {
            responseBody = webClient.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (WebClientResponseException ex) {
            throw new RuntimeException("watsonx.ai HTTP " + ex.getStatusCode() + " error");
        }
        return extractGeneratedText(responseBody);
    }

    private String extractGeneratedText(String responseBody) throws Exception {
        if (responseBody == null || responseBody.isBlank()) {
            throw new IllegalStateException("Empty response from watsonx.ai");
        }
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode results = root.path("results");
        if (results.isArray() && results.size() > 0) {
            JsonNode text = results.get(0).path("generated_text");
            if (!text.isMissingNode() && !text.asText().isBlank()) {
                return text.asText().trim();
            }
        }
        throw new IllegalStateException("watsonx.ai response did not contain generated_text");
    }

    // ── Fallback Brief ────────────────────────────────────────────────────────

    private String buildFallbackBrief(OpsStats stats) {
        StringBuilder sb = new StringBuilder();
        sb.append("Operations Brief — System Generated\n\n");
        sb.append(stats.getActiveShipments()).append(" active shipments are being monitored.\n");
        if (stats.getDisruptedShipments() > 0) {
            sb.append(stats.getDisruptedShipments()).append(" shipments are affected by active disruptions.\n");
        }
        if (stats.getActiveDisruptions() > 0) {
            sb.append(stats.getActiveDisruptions()).append(" disruption events are currently active.\n");
        }
        if (stats.getIdleFleetAssets() > 0) {
            sb.append(stats.getIdleFleetAssets()).append(" fleet assets are idle and available for redeployment.\n");
        }
        if (stats.getColdChainAlerts() > 0) {
            sb.append(stats.getColdChainAlerts()).append(" cold-chain shipments require monitoring");
            if (stats.getCriticalColdChainAlerts() > 0) {
                sb.append(", including ").append(stats.getCriticalColdChainAlerts()).append(" with critical breaches");
            }
            sb.append(".\n");
        }
        List<String> priorities = new java.util.ArrayList<>();
        if (stats.getDisruptedShipments() > 0) priorities.add("disrupted shipments");
        if (stats.getCriticalColdChainAlerts() > 0) priorities.add("critical cold-chain alerts");
        if (stats.getIdleFleetAssets() > 0) priorities.add("idle fleet redeployment");
        if (!priorities.isEmpty()) {
            sb.append("\nImmediate priorities: review ").append(String.join(", ", priorities)).append(".");
        }
        return sb.toString();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank()
                && projectId != null && !projectId.isBlank();
    }

    // ── Result record ─────────────────────────────────────────────────────────

    public record BriefResult(String text, boolean aiGenerated) {
        static BriefResult ai(String text)       { return new BriefResult(text, true);  }
        static BriefResult fallback(String text) { return new BriefResult(text, false); }
    }
}
package com.mgmtp.gives.service.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.dto.campaign.CampaignResultGenerateResponse;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.service.GeminiService;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import org.springframework.web.client.HttpStatusCodeException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiServiceImpl implements GeminiService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Value("${app.gemini.api-key}")
    private String apiKey;

    @Value("${app.gemini.models:gemini-2.5-flash,gemini-2.5-flash-lite,gemini-3-flash-preview,gemini-3.1-flash-lite}")
    private String modelsConfig;

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent";

    @PostConstruct
    void validateConfig() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("GEMINI_API_KEY is not configured — AI result generation will fail at runtime");
        }
    }

    private List<String> getModelPriorityList() {
        return Arrays.stream(modelsConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    @Override
    public CampaignResultGenerateResponse generateCampaignResultDraft(
            Campaign campaign, long totalRaised, long donorCount, long volunteerCount, double goalPercent) {
        if (apiKey == null || apiKey.isBlank()) {
            log.error("Gemini API key is not configured — cannot generate result draft");
            throw new AppException(ErrorCode.GEMINI_API_ERROR);
        }
        log.info("Calling Gemini API to generate result draft: campaignId={}", campaign.getId());

        String prompt = buildPrompt(campaign, totalRaised, donorCount, volunteerCount, goalPercent);
        Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of("responseMimeType", "application/json")
        );

        List<String> models = getModelPriorityList();
        for (String currentModel : models) {
            try {
                log.info("Trying Gemini model: {}, campaignId={}", currentModel, campaign.getId());
                GeminiApiResponse response = restClient.post()
                        .uri(GEMINI_URL, currentModel)
                        .header("x-goog-api-key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .retrieve()
                        .body(GeminiApiResponse.class);

                String generatedText = extractText(response);
                log.info("Gemini responded successfully: model={}, campaignId={}", currentModel, campaign.getId());
                return parseGeneratedText(generatedText);
            } catch (HttpStatusCodeException e) {
                int status = e.getStatusCode().value();
                if (status == 429 || status == 503) {
                    log.warn("Rate limit / unavailable for model {}, trying next. campaignId={}", currentModel, campaign.getId());
                } else if (status == 404) {
                    log.warn("Model {} not found (404), trying next. campaignId={}", currentModel, campaign.getId());
                } else {
                    log.error("HTTP {} from Gemini: model={}, campaignId={}, error={}", status, currentModel, campaign.getId(), e.getMessage());
                    throw new AppException(ErrorCode.GEMINI_API_ERROR);
                }
            } catch (AppException e) {
                throw e;
            } catch (Exception e) {
                log.error("Gemini API call failed: model={}, campaignId={}, error={}", currentModel, campaign.getId(), e.getMessage(), e);
                throw new AppException(ErrorCode.GEMINI_API_ERROR);
            }
        }

        log.error("All Gemini models rate limited. campaignId={}", campaign.getId());
        throw new AppException(ErrorCode.GEMINI_API_ERROR);
    }

    private String buildPrompt(Campaign campaign, long totalRaised, long donorCount,
                               long volunteerCount, double goalPercent) {
        return String.format("""
                You are a content writer for a charity platform. \
                Write a FINAL RESULT REPORT for a campaign that has already ended. \
                Be honest and factual about the numbers. \
                If donations or volunteers are 0, acknowledge it — do not speculate about the future.

                Campaign information:
                - Name: %s
                - Description: %s
                - Fundraising goal: %,d VND
                - Total raised: %,d VND (%.1f%% of goal)
                - Number of donors: %d
                - Number of volunteers: %d

                Return a JSON object with exactly these fields (write in English, naturally and sincerely):
                {
                  "resultSummary": "A retrospective summary of what happened during the campaign, what was achieved, and an honest reflection. 2-3 paragraphs.",
                  "itemsSummary": "Summary of non-monetary goods donated, or empty string if none.",
                  "acknowledgements": "A closing thank-you to anyone who participated. If no one donated or volunteered, keep it brief and genuine."
                }
                """,
                campaign.getTitle(),
                campaign.getDescription() != null ? campaign.getDescription() : "No description",
                campaign.getTarget() != null ? campaign.getTarget() : 0L,
                totalRaised,
                goalPercent,
                donorCount,
                volunteerCount
        );
    }

    private String extractText(GeminiApiResponse response) {
        if (response == null
                || response.getCandidates() == null
                || response.getCandidates().isEmpty()
                || response.getCandidates().get(0).getContent() == null
                || response.getCandidates().get(0).getContent().getParts() == null
                || response.getCandidates().get(0).getContent().getParts().isEmpty()) {
            log.error("Gemini API returned empty or invalid response");
            throw new AppException(ErrorCode.GEMINI_API_ERROR);
        }
        String text = response.getCandidates().get(0).getContent().getParts().get(0).getText();
        if (text == null) {
            log.error("Gemini API returned a response with no text content (possibly safety-filtered)");
            throw new AppException(ErrorCode.GEMINI_API_ERROR);
        }
        return text;
    }

    private CampaignResultGenerateResponse parseGeneratedText(String text) {
        String cleaned = text.trim();
        if (cleaned.startsWith("```")) {
            int newline = cleaned.indexOf('\n');
            cleaned = newline >= 0 ? cleaned.substring(newline + 1) : cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3).trim();
        }
        try {
            return objectMapper.readValue(cleaned, CampaignResultGenerateResponse.class);
        } catch (Exception e) {
            log.warn("Could not parse Gemini response as JSON, using raw text as resultSummary");
            return new CampaignResultGenerateResponse(cleaned, null, null);
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class GeminiApiResponse {
        private List<Candidate> candidates;

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        static class Candidate {
            private Content content;
        }

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        static class Content {
            private List<Part> parts;
        }

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        static class Part {
            private String text;
        }
    }
}

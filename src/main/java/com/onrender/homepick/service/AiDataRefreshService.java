package com.onrender.homepick.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 추천 데이터 갱신: GitHub Actions(ai-data-refresh.yml) 실행 및 진행 상태 조회
 * 크롤링·전처리·학습은 Actions 에서 Python 으로 수행 → apartments.json 커밋 → Render 자동 재배포
 */
@Service
public class AiDataRefreshService{

    private static final String WORKFLOW_FILE = "ai-data-refresh.yml";
    private static final Duration COOLDOWN = Duration.ofMinutes(10);
    private static final Duration STATUS_CACHE = Duration.ofSeconds(10);
    private static final Pattern GENERATED_AT = Pattern.compile("\"generatedAtEpoch\":(\\d+)");

    private final RestClient github;
    private final boolean configured;

    private Instant lastTriggeredAt = Instant.EPOCH;
    private Map<String, Object> cachedStatus;
    private Instant cachedAt = Instant.EPOCH;

    // Spring Boot 4 는 RestClient.Builder 빈을 기본 제공하지 않으므로 직접 생성
    public AiDataRefreshService(@Value("${github.actions.token:}") String token,
                                @Value("${github.actions.repo:}") String repo){
        this.configured = !token.isBlank() && !repo.isBlank();
        this.github = RestClient.builder()
                .baseUrl("https://api.github.com/repos/" + repo + "/actions")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
    }

    public boolean isConfigured(){
        return configured;
    }

    /** 갱신 실행. 실패 사유가 있으면 메시지 반환, 성공이면 null */
    public synchronized String trigger(){
        if (!configured) return "GitHub 연동 설정(GITHUB_ACTIONS_TOKEN, GITHUB_REPO)이 없습니다.";

        Duration elapsed = Duration.between(lastTriggeredAt, Instant.now());
        if (elapsed.compareTo(COOLDOWN) < 0) {
            long wait = COOLDOWN.minus(elapsed).toMinutes() + 1;
            return "최근에 갱신을 요청했습니다. 약 " + wait + "분 후 다시 시도해 주세요.";
        }
        Map<String, Object> run = latestRun();
        if (run != null && !"completed".equals(run.get("status"))) {
            return "이미 최신 데이터로 갱신하는 중입니다.";
        }

        github.post()
                .uri("/workflows/{file}/dispatches", WORKFLOW_FILE)
                .body(Map.of("ref", "main"))
                .retrieve()
                .toBodilessEntity();
        lastTriggeredAt = Instant.now();
        cachedAt = Instant.EPOCH; // 상태 캐시 무효화
        return null;
    }

    /**
     * 진행 상태
     * state: idle(기록 없음) | running(크롤링·학습 중) | deploying(학습 완료, 서버 반영 대기) | done | failed
     */
    public synchronized Map<String, Object> status(){
        if (Duration.between(cachedAt, Instant.now()).compareTo(STATUS_CACHE) < 0) return cachedStatus;

        Map<String, Object> result = new LinkedHashMap<>();
        long dataGeneratedAt = currentDataGeneratedAt();
        result.put("configured", configured);
        result.put("dataGeneratedAt", dataGeneratedAt);
        result.put("state", "idle");

        Map<String, Object> run = configured ? latestRun() : null;
        if (run != null) {
            String startedAt = String.valueOf(run.get("run_started_at"));
            result.put("runStartedAt", startedAt);

            if (!"completed".equals(run.get("status"))) {
                result.put("state", "running");
                result.put("step", currentStep(run.get("id")));
            } else if (!"success".equals(run.get("conclusion"))) {
                result.put("state", "failed");
            } else {
                boolean deployed = dataGeneratedAt >= Instant.parse(startedAt).getEpochSecond();
                result.put("state", deployed ? "done" : "deploying");
            }
        }

        cachedStatus = result;
        cachedAt = Instant.now();
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> latestRun(){
        Map<String, Object> body = github.get()
                .uri("/workflows/{file}/runs?per_page=1", WORKFLOW_FILE)
                .retrieve()
                .body(Map.class);
        List<Map<String, Object>> runs = body == null ? List.of() : (List<Map<String, Object>>) body.get("workflow_runs");
        return runs == null || runs.isEmpty() ? null : runs.get(0);
    }

    @SuppressWarnings("unchecked")
    private String currentStep(Object runId){
        Map<String, Object> body = github.get()
                .uri("/runs/{id}/jobs", runId)
                .retrieve()
                .body(Map.class);
        List<Map<String, Object>> jobs = body == null ? List.of() : (List<Map<String, Object>>) body.get("jobs");
        if (jobs == null || jobs.isEmpty()) return "대기 중";
        List<Map<String, Object>> steps = (List<Map<String, Object>>) jobs.get(0).get("steps");
        if (steps == null) return "준비 중";
        return steps.stream()
                .filter(s -> "in_progress".equals(s.get("status")))
                .map(s -> String.valueOf(s.get("name")))
                .findFirst()
                .orElse("준비 중");
    }

    /** 현재 서버에 배포된 apartments.json 의 생성 시각(epoch 초), 없으면 0 */
    private long currentDataGeneratedAt(){
        ClassPathResource resource = new ClassPathResource("static/data/apartments.json");
        if (!resource.exists()) return 0;
        try (InputStream in = resource.getInputStream()) {
            String head = new String(in.readNBytes(300), StandardCharsets.UTF_8);
            Matcher m = GENERATED_AT.matcher(head);
            return m.find() ? Long.parseLong(m.group(1)) : 0;
        } catch (IOException e) {
            return 0;
        }
    }
}

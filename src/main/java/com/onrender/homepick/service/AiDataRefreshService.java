package com.onrender.homepick.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 추천 데이터 갱신
 * 1) GitHub Actions(ai-data-refresh.yml) 실행: 크롤링·전처리·학습(Python) → apartments.json 을 main 에 커밋
 * 2) 학습 결과는 서버가 GitHub main 에서 직접 받아 메모리에 보관·제공 (재배포 없이 로컬/Render 모두 즉시 반영)
 *    GitHub 에 접근할 수 없으면 jar 에 포함된 static/data/apartments.json 사용
 */
@Service
public class AiDataRefreshService{

    private static final String WORKFLOW_FILE = "ai-data-refresh.yml";
    private static final String DATA_PATH = "src/main/resources/static/data/apartments.json";
    private static final Duration COOLDOWN = Duration.ofMinutes(10);
    private static final Duration STATUS_CACHE = Duration.ofSeconds(4);
    private static final Duration DATA_CACHE = Duration.ofMinutes(30);
    private static final Duration RUN_STALE = Duration.ofMinutes(35);   // 이 시간 넘게 '진행 중'이면 실패로 간주 (Actions 제한 30분)
    private static final Duration SYNC_LIMIT = Duration.ofMinutes(15);  // 학습 완료 후 이 시간 안에 반영 안 되면 실패
    private static final Pattern GENERATED_AT = Pattern.compile("\"generatedAtEpoch\":(\\d+)");

    private final RestClient actions;
    private final RestClient contents;
    private final boolean configured;

    private Instant lastTriggeredAt = Instant.EPOCH;
    private Map<String, Object> cachedStatus;
    private Instant statusCachedAt = Instant.EPOCH;

    private byte[] data;
    private long dataEpoch;
    private Instant dataFetchedAt = Instant.EPOCH;

    // Spring Boot 4 는 RestClient.Builder 빈을 기본 제공하지 않으므로 직접 생성
    public AiDataRefreshService(@Value("${github.actions.token:}") String token,
                                @Value("${github.actions.repo:}") String repo){
        this.configured = !token.isBlank() && !repo.isBlank();
        this.actions = github(token, "https://api.github.com/repos/" + repo + "/actions", "application/vnd.github+json");
        this.contents = github(token, "https://api.github.com/repos/" + repo + "/contents", "application/vnd.github.raw+json");
    }

    private static RestClient github(String token, String baseUrl, String accept){
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.ACCEPT, accept)
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28");
        if (!token.isBlank()) builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return builder.build();
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
        if (run != null && isRunning(run) && !isStale(run)) {
            return "이미 최신 데이터로 갱신하는 중입니다.";
        }

        actions.post()
                .uri("/workflows/{file}/dispatches", WORKFLOW_FILE)
                .body(Map.of("ref", "main"))
                .retrieve()
                .toBodilessEntity();
        lastTriggeredAt = Instant.now();
        statusCachedAt = Instant.EPOCH; // 상태 캐시 무효화
        System.out.println(">> [AI 재학습] GitHub Actions(" + WORKFLOW_FILE + ") 실행 요청");
        return null;
    }

    /* =================================================================
     * 실패 원인 분석: 실패한 단계 + Actions 로그의 마지막 오류 문장 → 터미널 출력, 화면에는 요약
     * ================================================================= */
    private static final Pattern ERROR_LINE = Pattern.compile("([\\w.]*(Error|Exception|Timeout)\\w*: .+|.*API 오류.*)");
    private static final Pattern LOG_TIMESTAMP = Pattern.compile("^\\S+Z\\s");

    private final Map<Object, Map<String, String>> failures = new LinkedHashMap<>();
    private final Set<String> logged = new HashSet<>();

    private Map<String, String> failure(Map<String, Object> run){
        return failures.computeIfAbsent(run.get("id"), id -> {
            String step = "알 수 없는 단계";
            String error = null;
            try {
                Map<String, Object> job = firstJob(id);
                if (job != null) {
                    step = steps(job).stream()
                            .filter(s -> "failure".equals(s.get("conclusion")))
                            .map(s -> String.valueOf(s.get("name")))
                            .findFirst().orElse(step);
                    error = errorLine(job.get("id"));
                }
            } catch (RestClientException e) {
                error = "Actions 로그를 가져오지 못함: " + e.getMessage();
            }

            System.err.println(">> [AI 재학습 실패] 단계: " + step);
            System.err.println(">>   원인: " + (error == null ? "로그에서 오류 문장을 찾지 못함" : error));
            System.err.println(">>   상세 로그: " + run.get("html_url"));

            Map<String, String> info = new LinkedHashMap<>();
            info.put("step", step);
            info.put("message", step + " 단계 실패: " + summarize(error));
            return info;
        });
    }

    /** Actions 작업 로그에서 마지막 예외/오류 문장 추출 */
    private String errorLine(Object jobId){
        // 로그 API 는 다운로드 주소로 302 리다이렉트 → 주소만 받아 인증 없이 다운로드
        URI location = actions.get().uri("/jobs/{id}/logs", jobId)
                .retrieve().toBodilessEntity().getHeaders().getLocation();
        if (location == null) return null;
        String log = RestClient.create().get().uri(location).retrieve().body(String.class);
        if (log == null) return null;

        String[] lines = log.split("\\R");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = LOG_TIMESTAMP.matcher(lines[i]).replaceFirst("").trim();
            if (line.startsWith("##[error]Process completed")) continue;
            if (ERROR_LINE.matcher(line).matches()) return line.replace("##[error]", "");
        }
        return null;
    }

    /** 화면 표시용 짧은 원인 */
    private String summarize(String error){
        if (error == null) return "자세한 원인은 서버 로그를 확인해 주세요.";
        if (error.contains("ReadTimeout") || error.contains("timed out")) return "공공데이터 API 응답 시간 초과";
        if (error.contains("ConnectionError")) return "공공데이터 API 연결 실패";
        if (error.contains("CrawlError")) return error.replaceFirst("^.*CrawlError: ", "");
        if (error.contains("API 오류")) return error.replaceFirst("^.*(API 오류)", "$1");
        return error.length() > 120 ? error.substring(0, 120) + "..." : error;
    }

    private void logOnce(Map<String, Object> run, String kind, String message){
        if (logged.add(run.get("id") + ":" + kind)) System.err.println(message);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstJob(Object runId){
        Map<String, Object> body = actions.get().uri("/runs/{id}/jobs", runId).retrieve().body(Map.class);
        List<Map<String, Object>> jobs = body == null ? List.of() : (List<Map<String, Object>>) body.get("jobs");
        return jobs == null || jobs.isEmpty() ? null : jobs.get(0);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> steps(Map<String, Object> job){
        List<Map<String, Object>> steps = (List<Map<String, Object>>) job.get("steps");
        return steps == null ? List.of() : steps;
    }

    /**
     * 진행 상태
     * state: idle(기록 없음) | running(크롤링·학습 중) | syncing(학습 완료, 서버 반영 중) | done | failed
     */
    public synchronized Map<String, Object> status(){
        if (Duration.between(statusCachedAt, Instant.now()).compareTo(STATUS_CACHE) < 0) return cachedStatus;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("configured", configured);
        result.put("state", "idle");

        Map<String, Object> run = configured ? latestRun() : null;
        if (run != null) {
            Instant startedAt = Instant.parse(String.valueOf(run.get("run_started_at")));
            result.put("runStartedAt", startedAt.toString());

            if (isRunning(run)) {
                if (isStale(run)) {
                    result.put("state", "failed");
                    result.put("message", "갱신 작업이 제한 시간을 넘겨 중단되었습니다.");
                    logOnce(run, "stale", ">> [AI 재학습 실패] 35분 넘게 진행 중 → 중단으로 처리 (" + run.get("html_url") + ")");
                } else {
                    result.put("state", "running");
                    result.put("step", currentStep(run.get("id")));
                }
            } else if (!"success".equals(run.get("conclusion"))) {
                Map<String, String> failure = failure(run);
                result.put("state", "failed");
                result.put("step", failure.get("step"));
                result.put("message", failure.get("message"));
            } else {
                // 학습 완료 → 서버 데이터가 그 이후 것인지 확인, 아니면 GitHub 에서 다시 받기
                if (dataEpoch() < startedAt.getEpochSecond()) reloadData();
                if (dataEpoch() >= startedAt.getEpochSecond()) {
                    result.put("state", "done");
                    logOnce(run, "done", ">> [AI 재학습 완료] 새 학습 데이터 반영 (generatedAtEpoch=" + dataEpoch + ")");
                } else {
                    Instant completedAt = Instant.parse(String.valueOf(run.get("updated_at")));
                    boolean tooLate = Duration.between(completedAt, Instant.now()).compareTo(SYNC_LIMIT) > 0;
                    result.put("state", tooLate ? "failed" : "syncing");
                    if (tooLate) {
                        result.put("message", "학습 결과를 서버에 반영하지 못했습니다.");
                        logOnce(run, "sync", ">> [AI 재학습 실패] 학습은 성공했지만 GitHub 에서 새 데이터를 받지 못함 ("
                                + DATA_PATH + ")");
                    }
                }
            }
        }
        result.put("dataGeneratedAt", dataEpoch());

        cachedStatus = result;
        statusCachedAt = Instant.now();
        return result;
    }

    /** 추천 데이터(JSON). 30분마다 GitHub 에서 최신본 확인, 없으면 null */
    public synchronized byte[] data(){
        if (data == null || Duration.between(dataFetchedAt, Instant.now()).compareTo(DATA_CACHE) > 0) reloadData();
        return data;
    }

    private long dataEpoch(){
        data();
        return dataEpoch;
    }

    /** GitHub main 의 최신 학습 결과 → 실패 시 기존 캐시 유지 → 캐시도 없으면 jar 포함본 */
    private void reloadData(){
        dataFetchedAt = Instant.now();
        try {
            byte[] latest = contents.get().uri("/" + DATA_PATH + "?ref=main").retrieve().body(byte[].class);
            if (latest != null && latest.length > 0) {
                setData(latest);
                return;
            }
        } catch (RestClientException e) {
            System.err.println(">> GitHub 추천 데이터 조회 실패: " + e.getMessage());
        }
        if (data == null) setData(bundledData());
    }

    private void setData(byte[] bytes){
        data = bytes;
        dataEpoch = 0;
        if (bytes == null) return;
        Matcher m = GENERATED_AT.matcher(new String(bytes, 0, Math.min(bytes.length, 300), StandardCharsets.UTF_8));
        if (m.find()) dataEpoch = Long.parseLong(m.group(1));
    }

    private byte[] bundledData(){
        ClassPathResource resource = new ClassPathResource("static/data/apartments.json");
        if (!resource.exists()) return null;
        try (InputStream in = resource.getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    private boolean isRunning(Map<String, Object> run){
        return !"completed".equals(run.get("status"));
    }

    private boolean isStale(Map<String, Object> run){
        Instant startedAt = Instant.parse(String.valueOf(run.get("run_started_at")));
        return Duration.between(startedAt, Instant.now()).compareTo(RUN_STALE) > 0;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> latestRun(){
        Map<String, Object> body = actions.get()
                .uri("/workflows/{file}/runs?per_page=1", WORKFLOW_FILE)
                .retrieve()
                .body(Map.class);
        List<Map<String, Object>> runs = body == null ? List.of() : (List<Map<String, Object>>) body.get("workflow_runs");
        return runs == null || runs.isEmpty() ? null : runs.get(0);
    }

    /**
     * 현재 단계: 진행 중인 단계, 없으면(단계 전환 순간) 마지막 완료 단계의 다음 단계.
     * GitHub 단계 정보가 순간적으로 어긋나도 '가장 많이 진행된 단계'를 기준으로 판단
     */
    private String currentStep(Object runId){
        Map<String, Object> job = firstJob(runId);
        if (job == null) return "대기 중";
        List<Map<String, Object>> steps = steps(job);
        String current = "준비 중";
        for (int i = 0; i < steps.size(); i++) {
            Map<String, Object> s = steps.get(i);
            if ("in_progress".equals(s.get("status"))) {
                current = String.valueOf(s.get("name"));
            } else if ("completed".equals(s.get("status")) && i + 1 < steps.size()) {
                current = String.valueOf(steps.get(i + 1).get("name"));
            }
        }
        return current;
    }
}

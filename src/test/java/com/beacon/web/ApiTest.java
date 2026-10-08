package com.beacon.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.beacon.adapter.Platform;
import com.beacon.chat.LlmProvider;
import com.beacon.job.Job;
import com.beacon.job.JobService;
import com.beacon.job.JobType;
import com.beacon.security.UrlGuard;
import com.beacon.snapshot.SnapshotService;
import com.beacon.store.Store;
import com.beacon.store.StoreRepository;
import com.beacon.testsupport.DemoStores;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Every endpoint in the API, with its error codes, on real recorded data. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiTest {

  /** DNS without the network: the demo store names resolve to a public address. */
  @TestConfiguration
  static class OfflineDns {
    @Bean
    @Primary
    UrlGuard testUrlGuard() {
      return new UrlGuard(
          host -> {
            if (host.endsWith("stevemadden.com") || host.endsWith("reebok.com")) {
              return List.of(InetAddress.getByName("23.227.38.74"));
            }
            if (host.matches("[0-9.]+") || host.equals("localhost")) {
              return List.of(InetAddress.getByName(host));
            }
            throw new UnknownHostException(host);
          },
          false);
    }
  }

  @Autowired MockMvc mvc;
  @Autowired StoreRepository stores;
  @Autowired SnapshotService snapshots;
  @MockBean JobService jobs;
  @MockBean LlmProvider llm;

  long sm;

  @BeforeEach
  void setUp() {
    sm = DemoStores.steveMadden(stores, snapshots);
    DemoStores.reebok(stores, snapshots);
    when(llm.isReachable()).thenReturn(false);
    when(llm.provider()).thenReturn("ollama");
    when(llm.model()).thenReturn("qwen2.5:7b");
    when(llm.baseUrl()).thenReturn("http://localhost:11434/v1");
  }

  @Test
  void demoProgress_notLoadingWhenSeedingIsOff() throws Exception {
    mvc.perform(get("/api/demo/progress"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.loading").value(false));
  }

  @Test
  void root_forwardsToDashboard() throws Exception {
    mvc.perform(get("/")).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
  }

  @Test
  void listAndGetStores() throws Exception {
    mvc.perform(get("/api/stores"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].domain", hasItem("www.stevemadden.com")));
    mvc.perform(get("/api/stores/" + sm))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName").value("Steve Madden"))
        .andExpect(jsonPath("$.currency").value("USD"))
        .andExpect(jsonPath("$.status").value("ACTIVE"));
  }

  @Test
  void unknownStore_404InErrorFormat() throws Exception {
    mvc.perform(get("/api/stores/999999"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("Not found"))
        .andExpect(jsonPath("$.hint").exists())
        .andExpect(jsonPath("$.path").value("/api/stores/999999"))
        .andExpect(jsonPath("$.timestamp").exists());
  }

  @Test
  void addStore_missingUrl_validationDetails() throws Exception {
    mvc.perform(post("/api/stores").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.details.errors[0].field").value("url"));
  }

  @Test
  void addStore_notAnAddress_invalidUrl() throws Exception {
    mvc.perform(
            post("/api/stores")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"ftp://x y\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_URL"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"localhost", "127.0.0.1", "169.254.169.254", "10.0.0.5"})
  void addStore_privateAddress_urlNotAllowed(String url) throws Exception {
    mvc.perform(
            post("/api/stores")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"" + url + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("URL_NOT_ALLOWED"));
  }

  @Test
  void addStore_alreadyTracked_409WithStoreId() throws Exception {
    mvc.perform(
            post("/api/stores")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"www.stevemadden.com\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("STORE_ALREADY_TRACKED"))
        .andExpect(jsonPath("$.details.storeId").value(sm));
  }

  @Test
  void refresh_startsOrJoinsJob() throws Exception {
    Job job = Job.queued(sm, JobType.REFRESH);
    when(jobs.start(eq(sm), eq(JobType.REFRESH))).thenReturn(new JobService.StartedJob(job, true));

    mvc.perform(post("/api/stores/" + sm + "/snapshot"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.jobId").value(job.getId().toString()))
        .andExpect(jsonPath("$.joinedExisting").value(true));
  }

  @Test
  void report_trimmedWithTotals() throws Exception {
    mvc.perform(get("/api/stores/" + sm + "/report"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.headline.text", startsWith("789 products are missing core sizes")))
        .andExpect(jsonPath("$.restock", hasSize(100)))
        .andExpect(jsonPath("$.restockTotal", greaterThan(100)))
        .andExpect(jsonPath("$.topActions", hasSize(5)))
        .andExpect(jsonPath("$.kpis.atRiskPerWeek.estimate").value(true))
        .andExpect(jsonPath("$.journey", hasSize(6)));
  }

  @Test
  void report_storeWithoutSnapshot_409ReportNotReady() throws Exception {
    Store pending =
        stores
            .findByDomain("pending.example.com")
            .orElseGet(
                () ->
                    stores.save(Store.adding("pending.example.com", "Pending", Platform.SHOPIFY)));

    mvc.perform(get("/api/stores/" + pending.getId() + "/report"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("REPORT_NOT_READY"));
  }

  @Test
  void restock_pagedAndSearchable() throws Exception {
    mvc.perform(get("/api/stores/" + sm + "/restock").param("limit", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rows", hasSize(2)))
        .andExpect(jsonPath("$.currency").value("USD"))
        .andExpect(jsonPath("$.total", greaterThan(2)));
    mvc.perform(get("/api/stores/" + sm + "/restock").param("q", "calora"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rows[0].title", containsString("CALORA")));
    mvc.perform(get("/api/stores/" + sm + "/restock").param("limit", "9999"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
  }

  @Test
  void restockCsv_sameColumnsAsTable() throws Exception {
    mvc.perform(get("/api/stores/" + sm + "/restock.csv"))
        .andExpect(status().isOk())
        .andExpect(
            header().string("Content-Disposition", containsString("stevemadden.com-restock.csv")))
        .andExpect(
            content()
                .string(startsWith("rank,product,type,product_url,sold_out_sizes,total_sizes")))
        .andExpect(content().string(containsString("\"CALORA BLACK LEATHER\"")));
  }

  @Test
  void sizeGapsChangesTrends() throws Exception {
    mvc.perform(get("/api/stores/" + sm + "/size-gaps").param("limit", "3"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rows", hasSize(3)));
    mvc.perform(get("/api/stores/" + sm + "/changes"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.events").isArray());
    mvc.perform(get("/api/stores/" + sm + "/trends"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.points[0].sizesSoldOutPct").value(33.9));
  }

  @Test
  void actionStatus_setAndValidated() throws Exception {
    mvc.perform(
            put("/api/stores/" + sm + "/actions/PROMOTED_SOLD_OUT")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DONE\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DONE"));
    mvc.perform(
            put("/api/stores/" + sm + "/actions/X")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"MAYBE\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    mvc.perform(
            put("/api/stores/999999/actions/X")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DONE\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void dismissedAction_leavesTopFive() throws Exception {
    String key = "RESTOCK:product:7289708249221";
    mvc.perform(
            put("/api/stores/" + sm + "/actions/" + key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DISMISSED\"}"))
        .andExpect(status().isOk());

    mvc.perform(get("/api/stores/" + sm + "/report"))
        .andExpect(jsonPath("$.topActions[*].actionKey", org.hamcrest.Matchers.not(hasItem(key))));

    mvc.perform(
            put("/api/stores/" + sm + "/actions/" + key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"TODO\"}"))
        .andExpect(status().isOk());
  }

  @Test
  void brief_printReadyHtml() throws Exception {
    mvc.perform(get("/api/stores/" + sm + "/brief"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
        .andExpect(content().string(containsString("Insight Brief · Steve Madden")))
        .andExpect(content().string(containsString("Estimate")));
  }

  @Test
  void benchmark_commonChecksOnly() throws Exception {
    mvc.perform(get("/api/benchmark"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stores", hasSize(greaterThan(1))))
        .andExpect(jsonPath("$.commonChecks", org.hamcrest.Matchers.not(hasItem("SEARCH_TEST"))));
  }

  @Test
  void chat_answersAndValidates() throws Exception {
    mvc.perform(
            post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"storeId\":" + sm + ",\"message\":\"What should I restock first?\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.provider").value("RULES"))
        .andExpect(jsonPath("$.toolsUsed[0]").value("get_restock_priorities"));
    mvc.perform(
            post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"storeId\":" + sm + "}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details.errors[0].field").value("message"));
    mvc.perform(
            post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"storeId\":999999,\"message\":\"hi\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void systemEndpoints() throws Exception {
    mvc.perform(get("/api/assumptions"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.version").value(org.hamcrest.Matchers.matchesPattern("[0-9a-f]{64}")))
        .andExpect(jsonPath("$.values.demand.weeklyDemandBaselineUnits").value(20));
    mvc.perform(get("/api/llm/status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.model").value("qwen2.5:7b"))
        .andExpect(jsonPath("$.reachable").value(false));
    mvc.perform(get("/api/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
    mvc.perform(get("/api/metrics")).andExpect(status().isOk());
  }

  @Test
  void openApi_listsEveryEndpoint() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/stores']").exists())
        .andExpect(jsonPath("$.paths['/api/stores/{storeId}/snapshot']").exists())
        .andExpect(jsonPath("$.paths['/api/jobs/{jobId}']").exists())
        .andExpect(jsonPath("$.paths['/api/stores/{storeId}/report']").exists())
        .andExpect(jsonPath("$.paths['/api/stores/{storeId}/restock.csv']").exists())
        .andExpect(jsonPath("$.paths['/api/stores/{storeId}/actions/{actionKey}']").exists())
        .andExpect(jsonPath("$.paths['/api/benchmark']").exists())
        .andExpect(jsonPath("$.paths['/api/chat']").exists())
        .andExpect(jsonPath("$.paths['/api/health']").exists());
  }

  @Test
  void jobProgress_viaService() throws Exception {
    Job job = Job.queued(sm, JobType.REFRESH);
    when(jobs.get(job.getId())).thenReturn(job);

    mvc.perform(get("/api/jobs/" + job.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("QUEUED"))
        .andExpect(jsonPath("$.type").value("REFRESH"));
    mvc.perform(get("/api/jobs/not-a-uuid")).andExpect(status().isBadRequest());
    when(jobs.get(org.mockito.ArgumentMatchers.any()))
        .thenThrow(new com.beacon.common.BeaconException(com.beacon.common.ErrorCode.NOT_FOUND));
    mvc.perform(get("/api/jobs/" + java.util.UUID.randomUUID())).andExpect(status().isNotFound());
  }
}

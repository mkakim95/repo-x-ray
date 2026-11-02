package dev.repoxray;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.*;

/** Offline HTTP/MVC contract smoke: only GitHub's transport is replaced, never the analyzer. */
class ApiIntegrationTest {
 private static final String SHA="a".repeat(40), APP="b".repeat(40), LIB="c".repeat(40);
 private static final String BASE="https://api.github.com/repos/fixture/project";
 private final ObjectMapper json=new ObjectMapper();

 @Test void analyzesFixtureThroughJsonApiAndCachesResult() throws Exception {
  var bodies=new HashMap<String,String>();
  bodies.put(BASE,"{\"private\":false,\"default_branch\":\"main\"}");
  bodies.put(BASE+"/commits/main","{\"sha\":\""+SHA+"\"}");
  bodies.put(BASE+"/git/trees/"+SHA+"?recursive=1",json.writeValueAsString(Map.of("truncated",false,"tree",List.of(
   Map.of("type","blob","path","app/pom.xml","sha",APP,"size",500),
   Map.of("type","blob","path","lib/pom.xml","sha",LIB,"size",500),
   Map.of("type","blob","path","app/src/Main.java","sha",SHA,"size",10)))));
  bodies.put(BASE+"/git/blobs/"+APP,blob("<project><modelVersion>4.0.0</modelVersion><groupId>fixture</groupId><artifactId>app</artifactId><version>1</version><dependencies><dependency><groupId>fixture</groupId><artifactId>lib</artifactId><version>1</version></dependency></dependencies></project>"));
  bodies.put(BASE+"/git/blobs/"+LIB,blob("<project><modelVersion>4.0.0</modelVersion><groupId>fixture</groupId><artifactId>lib</artifactId><version>1</version></project>"));
  HttpClient client=mock(HttpClient.class);
  when(client.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any())).thenAnswer(call->{
   HttpRequest request=call.getArgument(0);
   String body=bodies.get(request.uri().toString());
   assertNotNull(body,"Unexpected upstream request: "+request.uri());
   assertEquals("GET",request.method());
   @SuppressWarnings("unchecked") HttpResponse<byte[]> response=mock(HttpResponse.class);
   when(response.statusCode()).thenReturn(200);
   when(response.body()).thenReturn(body.getBytes(StandardCharsets.UTF_8));
   return response;
  });
  MockMvc api=standaloneSetup(new AnalysisController(new GitHubSource(client))).addFilters(new RequestSizeFilter()).build();
  var request=post("/api/analyze").contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"https://github.com/fixture/project\"}");
  var response=api.perform(request).andReturn().getResponse();
  assertEquals(200,response.getStatus(),response.getContentAsString());
  var report=json.readTree(response.getContentAsString());
  assertEquals(SHA,report.path("repository").path("ref").asText());
  assertEquals(2,report.path("stats").path("modules").asInt());
  assertEquals(1,report.path("stats").path("edges").asInt());
  assertEquals(2,report.path("stats").path("filesScanned").asInt());
  assertEquals(3,report.path("stats").path("totalFiles").asInt());
  assertEquals(1,report.path("stats").path("languages").path("Java").asInt());
  assertEquals("maven:app",report.path("edges").get(0).path("source").asText());
  assertEquals("maven:lib",report.path("edges").get(0).path("target").asText());
  assertEquals(0,report.path("cycles").size());
  var cached=api.perform(request).andReturn().getResponse();
  assertEquals(200,cached.getStatus());
  assertEquals(report,json.readTree(cached.getContentAsString()));
  verify(client,times(5)).send(any(HttpRequest.class),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
 }

 @Test void rejectsInvalidAndOversizedRequestsWithoutUpstreamAccess() throws Exception {
  HttpClient client=mock(HttpClient.class);
  MockMvc api=standaloneSetup(new AnalysisController(new GitHubSource(client))).addFilters(new RequestSizeFilter()).build();
  for(String body:List.of("{", "{\"url\":\"https://example.com/private/repo\"}")) {
   var response=api.perform(post("/api/analyze").contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse();
   assertEquals(400,response.getStatus());
   assertEquals("INVALID_INPUT",json.readTree(response.getContentAsString()).path("error").asText());
  }
  assertEquals(413,api.perform(post("/api/analyze").contentType(MediaType.APPLICATION_JSON).content("x".repeat(9000))).andReturn().getResponse().getStatus());
  assertEquals(200,api.perform(get("/api/health")).andReturn().getResponse().getStatus());
  verifyNoInteractions(client);
 }

 private String blob(String text) throws Exception {
  return json.writeValueAsString(Map.of("encoding","base64","content",Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8))));
 }
}

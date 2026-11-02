package dev.repoxray;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.*;

class GitLabIntegrationTest {
 private static final String BASE="https://gitlab.corp",SHA="a".repeat(40),BLOB="b".repeat(40),TREE=BASE+"/api/v4/projects/42/repository/tree";
 @Test void privateFixtureIsPinnedPaginatedAndNeverCached() throws Exception {
  HttpClient client=mock(HttpClient.class);List<HttpRequest> calls=new ArrayList<>();
  when(client.send(any(HttpRequest.class),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any())).thenAnswer(call->{
   HttpRequest request=call.getArgument(0);calls.add(request);assertEquals("GET",request.method());assertEquals("fixture-token",request.headers().firstValue("PRIVATE-TOKEN").orElseThrow());assertEquals("gitlab.corp",request.uri().getHost());
   String uri=request.uri().toString(),body,link="";
   if(uri.equals(BASE+"/api/v4/projects/team%2Fsub%2Frepo"))body="{\"id\":42,\"default_branch\":\"main\",\"visibility\":\"private\"}";
   else if(uri.equals(BASE+"/api/v4/projects/42/repository/commits/main"))body="{\"id\":\""+SHA+"\"}";
   else if(uri.equals(TREE+"?recursive=true&pagination=keyset&per_page=100&ref="+SHA)){body="[{\"type\":\"blob\",\"path\":\"pom.xml\",\"id\":\""+BLOB+"\"}]";link="<"+TREE+"?page_token=next123&ref="+SHA+">; rel=\"next\"";}
   else if(uri.equals(TREE+"?recursive=true&pagination=keyset&per_page=100&ref="+SHA+"&page_token=next123"))body="[{\"type\":\"blob\",\"path\":\"src/Main.java\",\"id\":\""+SHA+"\"}]";
   else if(uri.equals(BASE+"/api/v4/projects/42/repository/blobs/"+BLOB+"/raw"))body="<project><groupId>fixture</groupId><artifactId>repo</artifactId></project>";
   else throw new AssertionError("Unexpected URI "+uri);
   return response(200,body,link);
  });
  var api=standaloneSetup(new AnalysisController(new GitHubSource(mock(HttpClient.class)),new GitLabSource(BASE,"fixture-token",client))).addFilters(new LocalAccessFilter(),new RequestSizeFilter()).build();
  String first=null;
  for(int i=0;i<2;i++){
   var result=api.perform(post("/api/analyze").contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"https://gitlab.corp/team/sub/repo\"}")).andReturn().getResponse();
   assertEquals(200,result.getStatus(),result.getContentAsString());assertEquals("no-store",result.getHeader("Cache-Control"));
   var json=new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.getContentAsString());assertEquals("gitlab",json.path("repository").path("provider").asText());assertFalse(json.path("repository").path("shareable").asBoolean(true));assertEquals(SHA,json.path("repository").path("ref").asText());assertEquals(2,json.path("stats").path("totalFiles").asInt());assertEquals(1,json.path("stats").path("modules").asInt());assertFalse(result.getContentAsString().contains("fixture-token"));
   if(first!=null)assertEquals(first,result.getContentAsString());first=result.getContentAsString();
  }
  assertEquals(10,calls.size(),"private request must reauthorize/refetch, never cache");
 }
 @Test void rejectsUnsafePaginationAndRedirects() throws Exception {
  for(String link:List.of("<https://evil.test/tree?page_token=x>; rel=\"next\"","<"+TREE+"?page_token=x&page_token=y>; rel=\"next\""))assertThrows(GitHubSource.Failure.class,()->GitLabSource.nextCursor(link,TREE));
  HttpClient client=mock(HttpClient.class);when(client.send(any(HttpRequest.class),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any())).thenReturn(response(302,"",""));
  var source=new GitLabSource(BASE,"fixture-token",client);var error=assertThrows(GitHubSource.Failure.class,()->source.analyze(source.parse(BASE+"/team/repo",null)));assertEquals("GITLAB_REDIRECT",error.code);verify(client,times(1)).send(any(HttpRequest.class),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
 }
 @Test void streamedResponseLimitCancelsBeforeAccumulation(){
  var subscriber=new GitHubSource.LimitedBody(8);var subscription=mock(java.util.concurrent.Flow.Subscription.class);subscriber.onSubscribe(subscription);subscriber.onNext(List.of(java.nio.ByteBuffer.wrap(new byte[9])));verify(subscription).cancel();assertThrows(java.util.concurrent.CompletionException.class,()->subscriber.getBody().toCompletableFuture().join());
 }
 @Test void invalidConfiguredTokenErrorDoesNotDiscloseSecret(){
  String secret="do-not-disclose\nsecret";var error=assertThrows(IllegalArgumentException.class,()->new GitLabSource(BASE,secret,mock(HttpClient.class)));assertFalse(error.getMessage().contains("do-not-disclose"));
 }
 @Test void oversizedBlobIsSkippedWithoutLosingWholeReport() throws Exception {
  HttpClient client=mock(HttpClient.class);
  when(client.send(any(HttpRequest.class),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any())).thenAnswer(call->{
   HttpRequest request=call.getArgument(0);String path=request.uri().getPath();
   if(path.endsWith("/raw"))throw new java.io.IOException(new java.io.IOException("Response too large"));
   if(path.endsWith("/commits/main"))return response(200,"{\"id\":\""+SHA+"\"}","");
   if(path.endsWith("/repository/tree"))return response(200,"[{\"type\":\"blob\",\"path\":\"pom.xml\",\"id\":\""+BLOB+"\"}]","");
   return response(200,"{\"id\":42,\"default_branch\":\"main\"}","");
  });
  var source=new GitLabSource(BASE,"fixture-token",client);var result=source.analyze(source.parse(BASE+"/team/repo",null));assertTrue(result.nodes().isEmpty());assertTrue(result.warnings().stream().anyMatch(w->w.contains("oversized")));
 }
 @Test void localAccessRejectsRebindingAndCrossOrigin() throws Exception {
  for(String[] values:new String[][]{{"attacker.example",null},{"localhost","https://attacker.example"},{"localhost","null"}}){
   var request=new MockHttpServletRequest("POST","/api/analyze");request.setServerName(values[0]);if(values[1]!=null)request.addHeader("Origin",values[1]);var response=new MockHttpServletResponse();boolean[] called={false};new LocalAccessFilter().doFilter(request,response,(r,s)->called[0]=true);assertEquals(403,response.getStatus());assertFalse(called[0]);
  }
 }
 private HttpResponse<byte[]> response(int status,String body,String link){
  @SuppressWarnings("unchecked") HttpResponse<byte[]> response=mock(HttpResponse.class);when(response.statusCode()).thenReturn(status);when(response.body()).thenReturn(body.getBytes(StandardCharsets.UTF_8));when(response.headers()).thenReturn(HttpHeaders.of(link.isEmpty()?Map.of():Map.of("Link",List.of(link)),(a,b)->true));return response;
 }
}

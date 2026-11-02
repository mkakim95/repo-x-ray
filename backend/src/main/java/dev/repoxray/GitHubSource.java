package dev.repoxray;
import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import static dev.repoxray.Model.*;

public final class GitHubSource {
 public static final int MAX_FILES=100,MAX_FILE_BYTES=512*1024,MAX_TOTAL_BYTES=8*1024*1024;
 private final HttpClient client;
 public GitHubSource(){this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build());}
 GitHubSource(HttpClient client){this.client=client;}
 private final ObjectMapper json=new ObjectMapper();
 private final String token=System.getenv("GITHUB_TOKEN");
 public static final class Failure extends RuntimeException {final int status;final String code;public Failure(int s,String c,String m){super(m);status=s;code=c;}}
 public Analysis analyze(RepoInput input) {
  long deadline=System.nanoTime()+Duration.ofSeconds(45).toNanos();
  String base="/repos/"+input.owner()+"/"+input.name();
  JsonNode metadata=get(base,1024*1024,deadline);
  if(metadata.path("private").asBoolean(true))throw new Failure(400,"PUBLIC_ONLY","Only public repositories may be analyzed.");
  String ref=input.ref()==null?metadata.path("default_branch").asText():input.ref();
  // Resolve immutable commit first, keeping every blob from one snapshot.
  JsonNode commit=get(base+"/commits/"+encode(ref),1024*1024,deadline);
  String sha=commit.path("sha").asText();
  if(!sha.matches("[a-fA-F0-9]{40}"))throw new Failure(502,"GITHUB_RESPONSE","GitHub returned an invalid commit.");
  JsonNode tree=get(base+"/git/trees/"+sha+"?recursive=1",MAX_TOTAL_BYTES,deadline);
  List<String> warnings=new ArrayList<>();if(tree.path("truncated").asBoolean())warnings.add("GitHub truncated this large repository tree. Results are partial.");
  TreeMap<String,JsonNode> candidates=new TreeMap<>(Comparator.comparingInt((String p)->p.endsWith("settings.gradle")||p.endsWith("settings.gradle.kts")?0:1).thenComparing(Comparator.naturalOrder()));int totalFiles=0;Map<String,Integer> languages=new TreeMap<>();
  for(JsonNode item:tree.path("tree")) {
   if(!item.path("type").asText().equals("blob"))continue;
   totalFiles++;String path=item.path("path").asText();String language=language(path);if(language!=null)languages.merge(language,1,Integer::sum);
   String file=path.substring(path.lastIndexOf('/')+1);
   if(Set.of("pom.xml","build.gradle","build.gradle.kts","settings.gradle","settings.gradle.kts").contains(file))candidates.put(path,item);
  }
  if(candidates.size()>MAX_FILES)warnings.add("Build file limit reached: inspecting first 100 of "+candidates.size()+" (settings first, then lexical order). Results are partial.");
  Map<String,String> files=new LinkedHashMap<>();int bytes=0,count=0;
  for(var entry:candidates.entrySet()) {
   if(count++>=MAX_FILES)break;
   if(System.nanoTime()>deadline-Duration.ofSeconds(2).toNanos()){warnings.add("Analysis time budget reached. Results are partial.");break;}
   if(entry.getValue().path("size").asLong()>MAX_FILE_BYTES){warnings.add("Skipped oversized build file: "+entry.getKey());continue;}
   String blob=entry.getValue().path("sha").asText();if(!blob.matches("[a-fA-F0-9]{40}"))continue;
   JsonNode content;
   try {content=get(base+"/git/blobs/"+blob,MAX_FILE_BYTES*2,deadline);}
   catch(Failure e){if(e.status==429 && !files.isEmpty()){warnings.add("GitHub rate limit reached. Results are partial; configure GITHUB_TOKEN or retry later.");break;}throw e;}
   if(!content.path("encoding").asText().equals("base64")){warnings.add("Skipped unsupported content encoding: "+entry.getKey());continue;}
   byte[] decoded;try{decoded=Base64.getMimeDecoder().decode(content.path("content").asText());}catch(IllegalArgumentException e){throw new Failure(502,"GITHUB_RESPONSE","Invalid blob encoding from GitHub.");}
   if(decoded.length>MAX_FILE_BYTES){warnings.add("Skipped oversized build file: "+entry.getKey());continue;}
   bytes+=decoded.length;if(bytes>MAX_TOTAL_BYTES){warnings.add("Total source size limit reached. Results are partial.");break;}
   files.put(entry.getKey(),new String(decoded,StandardCharsets.UTF_8));
  }
  Analysis result=new StaticAnalyzer().analyze(new Repository(input.owner(),input.name(),input.url(),sha),files,warnings);
  Stats s=result.stats();return new Analysis(result.repository(),result.nodes(),result.edges(),result.cycles(),new Stats(s.modules(),s.edges(),s.externalDependencies(),s.cycles(),s.filesScanned(),totalFiles,languages),result.warnings());
 }
 private JsonNode get(String path,int limit,long deadline) {
  long remaining=deadline-System.nanoTime();if(remaining<=0)throw new Failure(504,"TIMEOUT","Analysis exceeded its time budget.");
  HttpRequest.Builder request=HttpRequest.newBuilder(URI.create("https://api.github.com"+path)).timeout(Duration.ofNanos(Math.min(remaining,Duration.ofSeconds(12).toNanos()))).header("Accept","application/vnd.github+json").header("X-GitHub-Api-Version","2022-11-28").header("User-Agent","Repo-X-Ray");
  if(token!=null&&!token.isBlank())request.header("Authorization","Bearer "+token);
  try {
   HttpResponse<byte[]> response=client.send(request.GET().build(),info->new LimitedBody(limit));
   int status=response.statusCode();
   if(status==404)throw new Failure(404,"NOT_FOUND","Public repository or ref not found.");
   if(status==403||status==429)throw new Failure(429,"GITHUB_LIMIT","GitHub denied this request or its rate limit was reached. Retry later or configure GITHUB_TOKEN.");
   if(status<200||status>=300)throw new Failure(502,"GITHUB_ERROR","GitHub could not serve this repository (HTTP "+status+").");
   return json.readTree(response.body());
  }catch(HttpTimeoutException e){throw new Failure(504,"TIMEOUT","GitHub request timed out. Try a smaller repository.");}
  catch(InterruptedException e){Thread.currentThread().interrupt();throw new Failure(503,"INTERRUPTED","Analysis interrupted.");}
  catch(java.io.IOException e){throw new Failure(502,"GITHUB_IO","Unable to read GitHub response, or response exceeded safe size limits.");}
 }
 private static String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8).replace("+","%20");}
 static String language(String p){int dot=p.lastIndexOf('.');if(dot<0)return null;return switch(p.substring(dot+1)){case "java"->"Java";case "kt","kts"->"Kotlin";case "groovy"->"Groovy";case "js","jsx","mjs"->"JavaScript";case "ts","tsx"->"TypeScript";case "py"->"Python";case "go"->"Go";case "rs"->"Rust";case "c","h"->"C";case "cpp","hpp","cc"->"C++";case "rb"->"Ruby";case "scala"->"Scala";default->null;};}
 static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
  private final HttpResponse.BodySubscriber<byte[]> delegate=HttpResponse.BodySubscribers.ofByteArray();private final int max;private long seen;private Flow.Subscription subscription;
  LimitedBody(int max){this.max=max;}
  public CompletionStage<byte[]> getBody(){return delegate.getBody();}
  public void onSubscribe(Flow.Subscription s){subscription=s;delegate.onSubscribe(s);}
  public void onNext(List<ByteBuffer> items){for(ByteBuffer b:items)seen+=b.remaining();if(seen>max){subscription.cancel();delegate.onError(new java.io.IOException("Response too large"));}else delegate.onNext(items);}
  public void onError(Throwable t){delegate.onError(t);}public void onComplete(){delegate.onComplete();}
 }
}

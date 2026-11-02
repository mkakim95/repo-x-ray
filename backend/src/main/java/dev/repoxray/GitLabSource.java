package dev.repoxray;
import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.*;
import static dev.repoxray.Model.*;
import static dev.repoxray.GitHubSource.*;

/** Read-only self-managed GitLab API. No metadata URL or pagination URL is followed. */
public final class GitLabSource {
 private final String base,token;private final HttpClient client;private final ObjectMapper json=new ObjectMapper();
 public GitLabSource(){this(System.getenv("GITLAB_BASE_URL"),System.getenv("GITLAB_TOKEN"),HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build());}
 GitLabSource(String base,String token,HttpClient client){this.base=GitLabInput.validateBase(base);if(token!=null&&!token.isBlank()&&!token.matches("[\u0021-\u007e]{1,4096}"))throw new IllegalArgumentException("GITLAB_TOKEN must be a nonempty printable ASCII secret without whitespace.");this.token=token;this.client=client;}
 public GitLabInput parse(String url,String ref){return GitLabInput.parse(url,ref,base);}
 public Analysis analyze(GitLabInput input) {
  if(base==null||!base.equals(input.base()))throw new Failure(400,"INVALID_INPUT","GitLab instance is not configured.");
  if(token==null||token.isBlank())throw new Failure(503,"GITLAB_NOT_CONFIGURED","Operator must configure the server-side GitLab read_api token.");
  long deadline=System.nanoTime()+Duration.ofSeconds(45).toNanos();String projectPath="/api/v4/projects/"+encode(input.project());
  JsonNode project=readJson(get(projectPath,1024*1024,deadline).body());
  String projectId=project.path("id").asText();if(!projectId.matches("[1-9][0-9]{0,18}"))throw new Failure(502,"GITLAB_RESPONSE","GitLab returned an invalid project ID.");
  projectPath="/api/v4/projects/"+projectId;
  String ref=input.ref()==null?project.path("default_branch").asText():input.ref();
  if(ref.isBlank())throw new Failure(404,"NOT_FOUND","Repository has no default branch or commit.");
  if(ref.length()>200)throw new Failure(502,"GITLAB_RESPONSE","GitLab returned an unsupported default branch.");
  JsonNode commit=readJson(get(projectPath+"/repository/commits/"+encode(ref),1024*1024,deadline).body());String sha=commit.path("id").asText();
  if(!sha.matches("[a-fA-F0-9]{40}"))throw new Failure(502,"GITLAB_RESPONSE","GitLab returned an invalid commit.");
  List<String> warnings=new ArrayList<>();warnings.add("Private/local GitLab analysis: sharing and server result caching are disabled. Keep this deployment restricted to your machine or authenticated users.");
  TreeMap<String,String> candidates=new TreeMap<>(Comparator.comparingInt((String p)->p.endsWith("settings.gradle")||p.endsWith("settings.gradle.kts")?0:1).thenComparing(Comparator.naturalOrder()));
  int totalFiles=0,treeBytes=0;Map<String,Integer> languages=new TreeMap<>();Set<String> seen=new HashSet<>(),seenPaths=new HashSet<>();String cursor=null;
  for(int page=0;page<100;page++) {
   if(System.nanoTime()>deadline-Duration.ofSeconds(5).toNanos()){warnings.add("GitLab tree time budget reached; results are partial.");break;}
   String treePath=projectPath+"/repository/tree?recursive=true&pagination=keyset&per_page=100&ref="+sha+(cursor==null?"":"&page_token="+encode(cursor));
   HttpResponse<byte[]> response;
   try{response=get(treePath,1024*1024,deadline);}catch(Failure e){if(e.status==429&&page>0){warnings.add("GitLab rate limit reached while reading tree; results are partial.");break;}throw e;}
   treeBytes+=response.body().length;if(treeBytes>MAX_TOTAL_BYTES){warnings.add("GitLab tree size budget reached; results are partial.");break;}
   JsonNode tree=readJson(response.body());if(!tree.isArray()||tree.size()>100)throw new Failure(502,"GITLAB_RESPONSE","GitLab returned an invalid tree page.");
   for(JsonNode item:tree) {
    if(!item.path("type").asText().equals("blob"))continue;
    String path=item.path("path").asText();if(path.length()>2000||!seenPaths.add(path))continue;
    totalFiles++;String language=GitHubSource.language(path);if(language!=null)languages.merge(language,1,Integer::sum);
    String filename=path.substring(path.lastIndexOf('/')+1),blob=item.path("id").asText();
    if(Set.of("pom.xml","build.gradle","build.gradle.kts","settings.gradle","settings.gradle.kts").contains(filename)&&blob.matches("[a-fA-F0-9]{40}"))candidates.put(path,blob);
   }
   cursor=nextCursor(response.headers().firstValue("Link").orElse(""),base+projectPath+"/repository/tree");
   if(cursor==null){if(tree.size()==100)warnings.add("GitLab returned a full tree page without a continuation link; tree may be partial.");break;}
   if(!seen.add(cursor))throw new Failure(502,"GITLAB_PAGINATION","GitLab repeated a pagination cursor.");
   if(page==99)warnings.add("GitLab tree limit (100 pages / 10000 entries) reached; results are partial.");
  }
  if(candidates.size()>MAX_FILES)warnings.add("Build/settings file limit (100) reached; results are partial.");
  Map<String,String> files=new LinkedHashMap<>();int totalBytes=0,count=0;
  for(var entry:candidates.entrySet()) {
   if(count++>=MAX_FILES)break;
   if(System.nanoTime()>deadline-Duration.ofSeconds(2).toNanos()){warnings.add("Analysis time budget reached; results are partial.");break;}
   byte[] data;try{data=get(projectPath+"/repository/blobs/"+entry.getValue()+"/raw",MAX_FILE_BYTES,deadline).body();}
   catch(Failure e){if(e.status==429&&!files.isEmpty()){warnings.add("GitLab rate limit reached; results are partial.");break;}if(e.code.equals("GITLAB_SIZE")){warnings.add("Skipped oversized build file: "+entry.getKey());continue;}throw e;}
   totalBytes+=data.length;if(totalBytes>MAX_TOTAL_BYTES){warnings.add("Build source size limit reached; results are partial.");break;}
   files.put(entry.getKey(),new String(data,StandardCharsets.UTF_8));
  }
  Analysis result=new StaticAnalyzer().analyze(new Repository(input.owner(),input.name(),input.url(),sha,"gitlab",false),files,warnings);Stats s=result.stats();
  return new Analysis(result.repository(),result.nodes(),result.edges(),result.cycles(),new Stats(s.modules(),s.edges(),s.externalDependencies(),s.cycles(),s.filesScanned(),totalFiles,languages),result.warnings());
 }
 private HttpResponse<byte[]> get(String path,int limit,long deadline) {
  long remaining=deadline-System.nanoTime();if(remaining<=0)throw new Failure(504,"TIMEOUT","Analysis exceeded its time budget.");
  // Only a validated operator base plus locally constructed API paths can reach transport.
  HttpRequest request=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofNanos(Math.min(remaining,Duration.ofSeconds(12).toNanos()))).header("PRIVATE-TOKEN",token).header("User-Agent","Repo-X-Ray").GET().build();
  try {
   HttpResponse<byte[]> response=client.send(request,info->new LimitedBody(limit));int status=response.statusCode();
   if(status==401||status==403)throw new Failure(403,"GITLAB_ACCESS","GitLab access denied. Operator must check token scope and project access.");
   if(status==404)throw new Failure(404,"NOT_FOUND","GitLab repository, ref or file was not found or is not accessible.");
   if(status==429)throw new Failure(429,"GITLAB_LIMIT","GitLab rate limit reached. Retry later.");
   if(status>=300&&status<400)throw new Failure(502,"GITLAB_REDIRECT","GitLab redirect refused. Configure its canonical HTTPS installation URL.");
   if(status<200||status>=300)throw new Failure(502,"GITLAB_ERROR","GitLab could not serve this repository.");
   return response;
  }catch(HttpTimeoutException e){throw new Failure(504,"TIMEOUT","GitLab request timed out.");}
  catch(InterruptedException e){Thread.currentThread().interrupt();throw new Failure(503,"INTERRUPTED","Analysis interrupted.");}
  catch(java.io.IOException e){for(Throwable cause=e;cause!=null;cause=cause.getCause())if("Response too large".equals(cause.getMessage()))throw new Failure(502,"GITLAB_SIZE","GitLab response exceeded its safe size limit.");throw new Failure(502,"GITLAB_IO","Unable to read GitLab response, or response exceeded safe size limits.");}
 }
 private JsonNode readJson(byte[] bytes){try{JsonNode value=json.readTree(bytes);if(value==null)throw new Failure(502,"GITLAB_RESPONSE","GitLab returned an empty JSON response.");return value;}catch(java.io.IOException e){throw new Failure(502,"GITLAB_RESPONSE","GitLab returned invalid JSON.");}}
 static String nextCursor(String link,String expectedTreeUrl) {
  if(link.length()>8192)throw new Failure(502,"GITLAB_PAGINATION","Invalid pagination metadata.");
  Matcher next=Pattern.compile("<([^>]+)>\\s*;\\s*rel=\"?next\"?").matcher(link);
  if(!next.find())return null;
  try {
   URI expected=URI.create(expectedTreeUrl),uri=URI.create(next.group(1));
   if(!Objects.equals(expected.getScheme(),uri.getScheme())||!Objects.equals(expected.getRawAuthority(),uri.getRawAuthority())||!Objects.equals(expected.getRawPath(),uri.getRawPath())||uri.getFragment()!=null)throw new IllegalArgumentException();
   String cursor=null;for(String part:Objects.requireNonNull(uri.getRawQuery()).split("&")){String[] pair=part.split("=",2);if(pair.length==2&&pair[0].equals("page_token")){if(cursor!=null)throw new IllegalArgumentException();cursor=URLDecoder.decode(pair[1],StandardCharsets.UTF_8);}}
   if(cursor==null||cursor.isBlank()||cursor.length()>1000)throw new IllegalArgumentException();return cursor;
  }catch(RuntimeException e){throw new Failure(502,"GITLAB_PAGINATION","GitLab returned unsafe or unsupported pagination metadata.");}
 }
 private static String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8).replace("+","%20");}
}

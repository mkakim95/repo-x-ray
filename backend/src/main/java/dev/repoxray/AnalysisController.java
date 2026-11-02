package dev.repoxray;
import java.time.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import static dev.repoxray.Model.*;
@RestController
@RequestMapping("/api")
public class AnalysisController {
 private final GitHubSource github=new GitHubSource();
 private final Semaphore slots=new Semaphore(2);
 private record Cached(Analysis result,Instant expires){}
 private final Map<RepoInput,Cached> cache=Collections.synchronizedMap(new LinkedHashMap<>());
 public record Request(String url,String ref){}
 public record Error(String error,String message){}
 @GetMapping("/health") public Map<String,String> health(){return Map.of("status","ok");}
 @PostMapping("/analyze") public Analysis analyze(@RequestBody Request request) {
  RepoInput input=RepoInput.parse(request.url(),request.ref());
  Cached hit=cache.get(input);if(hit!=null&&hit.expires.isAfter(Instant.now()))return hit.result;
  if(!slots.tryAcquire())throw new GitHubSource.Failure(429,"BUSY","Two analyses are already running. Please retry shortly.");
  try {Analysis result=github.analyze(input);synchronized(cache){if(cache.size()>=32)cache.remove(cache.keySet().iterator().next());cache.put(input,new Cached(result,Instant.now().plusSeconds(300)));}return result;}finally{slots.release();}
 }
 @ExceptionHandler(IllegalArgumentException.class) ResponseEntity<Error> invalid(IllegalArgumentException e){return ResponseEntity.badRequest().body(new Error("INVALID_INPUT",e.getMessage()));}
 @ExceptionHandler(HttpMessageNotReadableException.class) ResponseEntity<Error> malformed(){return ResponseEntity.badRequest().body(new Error("INVALID_INPUT","Send JSON with a repository url and optional ref."));}
 @ExceptionHandler(GitHubSource.Failure.class) ResponseEntity<Error> upstream(GitHubSource.Failure e){return ResponseEntity.status(e.status).body(new Error(e.code,e.getMessage()));}
}

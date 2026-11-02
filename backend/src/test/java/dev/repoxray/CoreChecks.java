package dev.repoxray;
import java.util.*;
import static dev.repoxray.Model.*;
/** Dependency-free executable regression tests; JUnit adapter runs these in Maven too. */
public final class CoreChecks {
 public static void main(String[] args){run();System.out.println("Core checks passed (URL validation, Maven, Gradle, cycles, XXE, determinism).");}
 static void check(boolean condition,String reason){if(!condition)throw new AssertionError(reason);}
 static void run(){
  check(RepoInput.parse("https://github.com/acme/demo.git/",null).name().equals("demo"),"Git suffix");
  for(String url:List.of("http://github.com/acme/demo","https://localhost/acme/demo","https://github.com.evil.org/acme/demo","https://evil@github.com/acme/demo","https://github.com:443/acme/demo","https://github.com/acme/demo?x=y","https://github.com/acme/demo/tree/main","https://github.com/acme/%2e%2e","https://github.com/acme/..")) {
   try{RepoInput.parse(url,null);throw new AssertionError("accepted "+url);}catch(IllegalArgumentException expected){}
  }
  try{RepoInput.parse("https://github.com/a/b","../x");throw new AssertionError("ref traversal");}catch(IllegalArgumentException expected){}
  Repository repo=new Repository("a","b","https://github.com/a/b","abc");
  StaticAnalyzer analyzer=new StaticAnalyzer();
  Map<String,String> files=Map.of("pom.xml",pom("root","a"),"a/pom.xml",pom("a","root"),"b/pom.xml","<project><parent><groupId>org.test</groupId></parent><artifactId>b</artifactId><dependencyManagement><dependencies><dependency><groupId>org.test</groupId><artifactId>a</artifactId></dependency></dependencies></dependencyManagement></project>");
  Analysis m=analyzer.analyze(repo,files,List.of());
  check(m.nodes().size()==3,"Maven modules");check(m.edges().size()==2,"Maven edges exclude dependencyManagement");check(m.cycles().size()==1&&m.cycles().getFirst().size()==2,"Maven cycle");
  check(m.equals(analyzer.analyze(repo,new HashMap<>(files),List.of())),"deterministic output");
  Analysis g=analyzer.analyze(repo,Map.of("build.gradle.kts","dependencies { implementation(project(\":app\")) }","app/build.gradle","dependencies { api project(':core')\n implementation 'org.slf4j:slf4j-api:2.0' }","core/build.gradle.kts","implementation(project(path = \":app\"))"),List.of());
  check(g.edges().size()==3,"Gradle Kotlin/Groovy edges");check(g.stats().externalDependencies()==1,"Gradle external stats");check(g.cycles().size()==1,"Gradle SCC");
  Analysis config=analyzer.analyze(repo,Map.of("build.gradle","project(':app') { apply plugin: 'java' }","app/build.gradle",""),List.of());check(config.edges().isEmpty(),"Gradle project configuration is not a dependency");
  Analysis duplicate=analyzer.analyze(repo,Map.of("build.gradle","","build.gradle.kts",""),List.of());check(duplicate.nodes().size()==1,"unique Gradle IDs");
  Analysis ambiguous=analyzer.analyze(repo,Map.of("pom.xml",pom("root","a"),"a/pom.xml",pom("a","none"),"example/pom.xml",pom("a","none")),List.of());check(ambiguous.edges().isEmpty(),"ambiguous Maven coordinate omitted");
  Analysis root=analyzer.analyze(repo,Map.of("build.gradle","","app/build.gradle","implementation project(':')"),List.of());check(root.edges().size()==1,"Gradle root dependency");
  Analysis strings=analyzer.analyze(repo,Map.of("build.gradle","def text = \"implementation project(':app')\"; notimplementation project(':app')","app/build.gradle",""),List.of());check(strings.edges().isEmpty(),"ignore strings and configuration suffixes");
  StringBuilder many=new StringBuilder();for(int i=0;i<700;i++)many.append("implementation 'org.example:lib").append(i).append(":1'\n");
  Analysis capped=analyzer.analyze(repo,Map.of("build.gradle",many.toString()),List.of());check(capped.nodes().getFirst().dependencies().size()==500,"per-module dependency cap");check(capped.warnings().stream().anyMatch(w->w.contains("limit")),"dependency cap disclosed");
  Analysis settings=analyzer.analyze(repo,Map.of("settings.gradle.kts","include(\":app\", \":api\", \":empty\")\nproject(\":api\").projectDir = file(\"libs/http\")","app/build.gradle.kts","implementation(project(\":api\"))","libs/http/build.gradle.kts","api(project(\":empty\"))"),List.of());
  check(settings.nodes().size()==4,"settings creates root and buildless modules");check(settings.edges().contains(new Edge("gradle:app","gradle:libs/http")),"projectDir alias resolves");check(settings.edges().contains(new Edge("gradle:libs/http","gradle:empty")),"mapped module outgoing alias resolves");
  Analysis nested=analyzer.analyze(repo,Map.of("settings.gradle","include ':lib'","lib/build.gradle","","sample/settings.gradle","include ':lib', ':app'\nproject(':lib').projectDir = new File(settingsDir, 'shared')","sample/app/build.gradle","implementation project(':lib')"),List.of());
  check(nested.edges().contains(new Edge("gradle:sample/app","gradle:sample/shared")),"nested settings scope");
  Analysis dynamic=analyzer.analyze(repo,Map.of("settings.gradle","include modules\ninclude ':safe'\nproject(':safe').projectDir = file('../escape')"),List.of());check(dynamic.warnings().stream().anyMatch(w->w.contains("Dynamic")),"dynamic includes warned");check(dynamic.nodes().stream().noneMatch(n->n.path().contains("..")),"settings traversal rejected");
  Analysis longIncludes=analyzer.analyze(repo,Map.of("settings.gradle","include("+"':a',".repeat(2500)+"':a')"),List.of());check(longIncludes.nodes().size()==2,"long include list validates linearly");
  Analysis unsafeMapping=analyzer.analyze(repo,Map.of("settings.gradle","include ':a', ':caller'\nproject(':a').projectDir = file('../outside')","caller/build.gradle","implementation project(':a')"),List.of());check(unsafeMapping.nodes().stream().noneMatch(n->n.id().equals("gradle:a"))&&unsafeMapping.edges().isEmpty(),"unsafe mapping cannot resolve default alias");
  Analysis extendedMapping=analyzer.analyze(repo,Map.of("settings.gradle","include ':a', ':caller'\nproject(':a').projectDir = file('libs').resolve('actual')","caller/build.gradle","implementation project(':a')"),List.of());check(extendedMapping.edges().isEmpty()&&extendedMapping.nodes().stream().noneMatch(n->n.id().equals("gradle:libs")),"unsupported RHS suffix never maps prefix");
  Analysis malformedMappings=analyzer.analyze(repo,Map.of("settings.gradle","include ':a'\n"+"project(':a').projectDir=".repeat(20000)),List.of());check(malformedMappings.warnings().size()<400,"malformed mapping scans and warnings bounded");
  Analysis hostile=analyzer.analyze(repo,Map.of("build.gradle","/* ".repeat(170000)),List.of());check(hostile.edges().isEmpty(),"unterminated comments bounded");
  Analysis x=analyzer.analyze(repo,Map.of("pom.xml","<!DOCTYPE project [<!ENTITY xxe SYSTEM 'file:///etc/passwd'>]><project><artifactId>&xxe;</artifactId></project>"),List.of());
  check(x.nodes().isEmpty()&&!x.warnings().isEmpty(),"reject XXE");
  Analysis self=analyzer.analyze(repo,Map.of("pom.xml",pom("a","a")),List.of());check(self.cycles().size()==1,"self cycle");
  check(analyzer.analyze(repo,Map.of(),List.of()).nodes().isEmpty(),"empty repo");
 }
 static String pom(String name,String dep){return "<project><groupId>org.test</groupId><artifactId>"+name+"</artifactId><dependencies><dependency><groupId>org.test</groupId><artifactId>"+dep+"</artifactId></dependency></dependencies></project>";}
}

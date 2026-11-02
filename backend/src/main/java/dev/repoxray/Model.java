package dev.repoxray;
import java.util.List;
public final class Model {
 private Model() {}
 public record Repository(String owner,String name,String url,String ref,String provider,boolean shareable) {
  public Repository(String owner,String name,String url,String ref){this(owner,name,url,ref,"github",true);}
 }
 public record Node(String id,String label,String path,String buildTool,List<String> dependencies) {}
 public record Edge(String source,String target) {}
 public record Stats(int modules,int edges,int externalDependencies,int cycles,int filesScanned,int totalFiles,java.util.Map<String,Integer> languages) {
 public Stats(int modules,int edges,int externalDependencies,int cycles,int filesScanned){this(modules,edges,externalDependencies,cycles,filesScanned,filesScanned,java.util.Map.of());}
 }
 public record Analysis(Repository repository,List<Node> nodes,List<Edge> edges,List<List<String>> cycles,Stats stats,List<String> warnings) {}
}

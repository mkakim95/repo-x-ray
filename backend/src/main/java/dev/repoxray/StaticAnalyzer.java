package dev.repoxray;
import java.io.StringReader;
import java.util.*;
import java.util.regex.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import static dev.repoxray.Model.*;

/** Never evaluates build scripts or downloads dependencies. */
public final class StaticAnalyzer {
 private record Module(String id,String label,String tool,String coordinate,List<String> deps) {}
 public Analysis analyze(Repository repo,Map<String,String> files,List<String> upstreamWarnings) {
  List<String> warnings=new ArrayList<>(upstreamWarnings);
  List<Module> modules=new ArrayList<>();
  for(var entry:new TreeMap<>(files).entrySet()) {
   String path=entry.getKey(), source=entry.getValue();
   String dir=path.contains("/")?path.substring(0,path.lastIndexOf('/')):"";
   try {
    if(path.endsWith("pom.xml")) modules.add(maven(dir,source));
    else if(path.endsWith("build.gradle") || path.endsWith("build.gradle.kts")) {
     if(path.endsWith("build.gradle") && files.containsKey(path+".kts")) {warnings.add("Both Gradle scripts found in "+(dir.isEmpty()?"root":dir)+"; inspecting build.gradle.kts only.");continue;}
     modules.add(gradle(dir,source));
    }
   } catch(Exception e) {warnings.add("Could not safely parse "+path+"; module omitted.");}
  }
  List<GradleSettings.Build> builds=GradleSettings.parse(files,warnings);
  Set<String> existing=new HashSet<>();for(Module m:modules)existing.add(m.id);
  for(var build:builds)for(var alias:new TreeMap<>(build.aliases()).entrySet()) {
   String id="gradle:"+alias.getValue();if(existing.add(id))modules.add(new Module(id,alias.getKey().equals(".")?"root":alias.getKey().replace('/',':'),"Gradle",null,List.of()));
  }
  modules.sort(Comparator.comparing(Module::id));
  // Maven and Gradle may coexist: distinct IDs preserve both rather than silently overwriting.
  Map<String,String> coordinates=new HashMap<>(), gradlePaths=new HashMap<>();
  Set<String> ambiguous=new HashSet<>();
  for(Module m:modules) {if(m.coordinate!=null && coordinates.putIfAbsent(m.coordinate,m.id)!=null)ambiguous.add(m.coordinate); if(m.tool.equals("Gradle"))gradlePaths.put(m.id.substring(7),m.id);}
  for(String coordinate:new TreeSet<>(ambiguous)){coordinates.remove(coordinate);warnings.add("Ambiguous Maven coordinate "+coordinate+" occurs in multiple modules; its internal edges are omitted.");}
  List<Node> nodes=new ArrayList<>(); Set<Edge> edges=new LinkedHashSet<>(); Set<String> external=new HashSet<>();
  int budget=10000,unresolved=0;
  for(Module m:modules) {
   List<String> deps=m.deps.subList(0,Math.min(budget,m.deps.size()));budget-=deps.size();
   if(m.deps.size()>=500)warnings.add("Dependency declaration limit (500) reached for "+m.id+"; results may be partial.");
   nodes.add(new Node(m.id,m.label,m.id.substring(m.id.indexOf(':')+1),m.tool,List.copyOf(deps)));
   for(String dep:deps) {
    boolean projectDependency=m.tool.equals("Gradle")&&dep.startsWith("project:");
    String target=projectDependency?GradleSettings.target(builds,m.id.substring(7),dep.substring(8),gradlePaths):coordinates.get(dep);
    if(target!=null)edges.add(new Edge(m.id,target)); else if(projectDependency){if(unresolved++<20)warnings.add("Unresolved Gradle project dependency "+dep+" from "+m.id+"; edge omitted.");} else external.add(dep);
   }
  }
  if(budget==0)warnings.add("Global dependency limit (10000) reached; results may be partial.");
  if(unresolved>20)warnings.add("Additional unresolved Gradle project dependencies: "+(unresolved-20));
  List<List<String>> cycles=strongComponents(nodes,new ArrayList<>(edges));
  if(modules.stream().anyMatch(m->m.tool.equals("Gradle")))warnings.add("Gradle is statically inspected: conditional declarations are approximated; computed includes, convention plugins, version catalogs and non-literal projectDir mappings are not resolved.");
  if(modules.stream().anyMatch(m->m.tool.equals("Maven")))warnings.add("Maven edges match groupId:artifactId only (versions ignored); direct dependencies only: profiles, dependencyManagement, inherited dependencies and property interpolation are not resolved. Every discovered pom.xml is shown, including examples.");
  if(nodes.isEmpty())warnings.add("No supported Maven or Gradle build files found.");
  return new Analysis(repo,nodes,new ArrayList<>(edges),cycles,new Stats(nodes.size(),edges.size(),external.size(),cycles.size(),files.size()),warnings);
 }
 private Module maven(String dir,String xml)throws Exception {
  var f=DocumentBuilderFactory.newInstance();
  f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
  f.setFeature("http://xml.org/sax/features/external-general-entities",false);
  f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
  f.setAttribute("http://www.oracle.com/xml/jaxp/properties/maxElementDepth",128);
  f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");f.setXIncludeAware(false);f.setExpandEntityReferences(false);
  var b=f.newDocumentBuilder();b.setErrorHandler(new org.xml.sax.helpers.DefaultHandler());
  Element root=b.parse(new InputSource(new StringReader(xml))).getDocumentElement();
  String artifact=text(root,"artifactId"),group=text(root,"groupId");
  if(group.isEmpty())group=text(child(root,"parent"),"groupId");
  Set<String> deps=new LinkedHashSet<>();Element dependencies=child(root,"dependencies");
  if(dependencies!=null) for(Element e:children(dependencies,"dependency")) {String g=text(e,"groupId"),a=text(e,"artifactId");if(!g.isEmpty()&&!a.isEmpty())deps.add(g+":"+a);if(deps.size()>=500)break;}
  return new Module("maven:"+(dir.isEmpty()?".":dir),artifact.isEmpty()?(dir.isEmpty()?"root":dir):artifact,"Maven",group+":"+artifact,List.copyOf(deps));
 }
 private Module gradle(String dir,String source) {
  source=stripComments(source);
  boolean[] outside=outsideStrings(source);
  Set<String> deps=new LinkedHashSet<>();
  Matcher projects=Pattern.compile("(?<![\\w$])(?:implementation|api|compileOnly|runtimeOnly|testImplementation|testRuntimeOnly|annotationProcessor)\\s*+\\(?\\s*+project\\s*+\\(\\s*+(?:path\\s*+[:=]\\s*+)?[\"'](:[\\w.:-]*)[\"']").matcher(source);
  while(deps.size()<500&&projects.find()){if(!outside[projects.start()])continue;String path=projects.group(1).substring(1).replace(':','/');deps.add("project:"+(path.isEmpty()?".":path));}
  Matcher ext=Pattern.compile("(?<![\\w$])(?:implementation|api|compileOnly|runtimeOnly|testImplementation|testRuntimeOnly|annotationProcessor)\\s*+\\(?\\s*+[\"']([\\w.-]+:[\\w.-]+):[^\"']+[\"']").matcher(source);
  while(deps.size()<500&&ext.find())if(outside[ext.start()])deps.add(ext.group(1));
  return new Module("gradle:"+(dir.isEmpty()?".":dir),dir.isEmpty()?"root":dir.substring(dir.lastIndexOf('/')+1),"Gradle",null,List.copyOf(deps));
 }
 // Linear scan: adversarial unterminated comments must not cause regex backtracking.
 static boolean[] outsideStrings(String source){
  boolean[] out=new boolean[source.length()];char quote=0;boolean escape=false;
  for(int i=0;i<source.length();i++){char c=source.charAt(i);out[i]=quote==0;if(quote!=0){if(escape)escape=false;else if(c=='\\')escape=true;else if(c==quote)quote=0;}else if(c=='\''||c=='"')quote=c;}
  return out;
 }
 static String stripComments(String source) {
  StringBuilder out=new StringBuilder(source.length());char quote=0;boolean line=false,block=false,escape=false;
  for(int i=0;i<source.length();i++) {
   char c=source.charAt(i),next=i+1<source.length()?source.charAt(i+1):0;
   if(line){if(c=='\n'){line=false;out.append(c);}else out.append(' ');continue;}
   if(block){if(c=='*'&&next=='/'){block=false;out.append("  ");i++;}else out.append(c=='\n'?'\n':' ');continue;}
   if(quote!=0){out.append(c);if(escape)escape=false;else if(c=='\\')escape=true;else if(c==quote)quote=0;continue;}
   if(c=='/'&&next=='/'){line=true;out.append("  ");i++;continue;}
   if(c=='/'&&next=='*'){block=true;out.append("  ");i++;continue;}
   if(c=='\''||c=='"')quote=c;out.append(c);
  }
  return out.toString();
 }
 private static Element child(Element e,String name) {if(e==null)return null;for(var n=e.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element el&&el.getTagName().equals(name))return el;return null;}
 private static List<Element> children(Element e,String name){List<Element> result=new ArrayList<>();for(var n=e.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element el&&el.getTagName().equals(name))result.add(el);return result;}
 private static String text(Element e,String name){Element c=child(e,name);return c==null?"":c.getTextContent().trim();}
 // SCCs are cycle groups, not an exponential enumeration of all simple cycles.
 static List<List<String>> strongComponents(List<Node> nodes,List<Edge> edges) {
  Map<String,List<String>> graph=new LinkedHashMap<>();for(Node n:nodes)graph.put(n.id(),new ArrayList<>());for(Edge e:edges)graph.get(e.source()).add(e.target());
  Map<String,Integer> index=new HashMap<>(),low=new HashMap<>();Deque<String> stack=new ArrayDeque<>();Set<String> active=new HashSet<>();List<List<String>> result=new ArrayList<>();int[] next={0};
  for(String v:graph.keySet())if(!index.containsKey(v))visit(v,graph,index,low,stack,active,result,next);
  return result;
 }
 private static void visit(String v,Map<String,List<String>> g,Map<String,Integer> idx,Map<String,Integer> low,Deque<String>s,Set<String>a,List<List<String>> out,int[] next){
  idx.put(v,next[0]);low.put(v,next[0]++);s.push(v);a.add(v);
  for(String w:g.get(v)){if(!idx.containsKey(w)){visit(w,g,idx,low,s,a,out,next);low.put(v,Math.min(low.get(v),low.get(w)));}else if(a.contains(w))low.put(v,Math.min(low.get(v),idx.get(w)));}
  if(low.get(v).equals(idx.get(v))){List<String> component=new ArrayList<>();String w;do{w=s.pop();a.remove(w);component.add(w);}while(!w.equals(v));if(component.size()>1||g.get(v).contains(v)){Collections.sort(component);out.add(component);}}
 }
}

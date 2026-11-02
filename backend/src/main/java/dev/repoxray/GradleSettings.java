package dev.repoxray;
import java.util.*;
import java.util.regex.*;
/** Literal-only Gradle settings. Paths are identifiers, never filesystem accesses. */
final class GradleSettings {
 record Build(String root,Map<String,String> aliases){}
 static List<Build> parse(Map<String,String> files,List<String> warnings) {
  List<Build> builds=new ArrayList<>();int total=0;
  for(var entry:new TreeMap<>(files).entrySet()) {
   String path=entry.getKey();if(!path.endsWith("settings.gradle")&&!path.endsWith("settings.gradle.kts"))continue;
   if(path.endsWith("settings.gradle")&&files.containsKey(path+".kts")){warn(warnings,"Both Gradle settings files found; inspecting "+path+".kts only.");continue;}
   String root=path.contains("/")?path.substring(0,path.lastIndexOf('/')):".";
   String source=StaticAnalyzer.stripComments(entry.getValue());boolean[] outside=StaticAnalyzer.outsideStrings(source);
   Map<String,String> aliases=new LinkedHashMap<>();aliases.put(".",root);
   Matcher includes=Pattern.compile("(?<![\\w$])include(?![\\w$])").matcher(source);
   while(includes.find()) {
    if(!outside[includes.start()])continue;
    int begin=includes.end();while(begin<source.length()&&Character.isWhitespace(source.charAt(begin)))begin++;
    boolean parenthesized=begin<source.length()&&source.charAt(begin)=='(';if(parenthesized)begin++;
    int end=begin;
    while(end<source.length()&&end-begin<=16384&&(parenthesized?source.charAt(end)!=')':source.charAt(end)!='\n'&&source.charAt(end)!='\r'&&source.charAt(end)!=';'))end++;
    boolean terminated=!parenthesized||end<source.length()&&source.charAt(end)==')';
    includes.region(Math.min(source.length(),end+1),source.length());
    String args=source.substring(begin,end);
    if(!terminated||args.length()>16384||!literalArguments(args)){warn(warnings,"Dynamic or unsupported include declaration in "+path+" omitted.");continue;}
    Matcher literal=Pattern.compile("[\"'](:?[\\w.:-]+)[\"']").matcher(args);
    while(literal.find()) {
     String alias=literal.group(1).replaceFirst("^:","").replace(':','/');
     if(!validPath(alias)){warn(warnings,"Unsafe project alias in "+path+" omitted.");continue;}
     String[] parts=alias.split("/");String partial="";
     for(String part:parts){partial=partial.isEmpty()?part:partial+"/"+part;if(!aliases.containsKey(partial)){if(++total>400)break;aliases.put(partial,join(root,partial));}}
     if(total>400)break;
    }
    if(total>400)break;
   }
   if(total>400)warn(warnings,"Gradle settings module limit (400) reached; results are partial.");
   // Supported RHS: file('dir'), File(rootDir, 'dir'), new File(settingsDir, 'dir').
   Matcher mapping=Pattern.compile("(?<![\\w$])project\\s*+\\(\\s*+[\"'](:?[\\w.:-]+)[\"']\\s*+\\)\\.projectDir\\s*+=").matcher(source);
   Pattern rhs=Pattern.compile("(?:file\\s*+\\(\\s*+|(?:new\\s++)?File\\s*+\\(\\s*+(?:rootDir|settingsDir)\\s*+,\\s*+)[\"']([\\w./-]+)[\"']\\s*+\\)\\s*+");
   while(mapping.find()) {
    if(!outside[mapping.start()])continue;
    String alias=mapping.group(1).replaceFirst("^:","").replace(':','/');if(alias.isEmpty())alias=".";
    int begin=mapping.end(),end=begin;while(end<source.length()&&end-begin<=16384&&source.charAt(end)!='\n'&&source.charAt(end)!='\r'&&source.charAt(end)!=';')end++;
    mapping.region(Math.min(source.length(),end+1),source.length());
    Matcher value=rhs.matcher(source.substring(begin,end).trim());
    if(!value.matches()||!validPath(value.group(1))){aliases.remove(alias);warn(warnings,"Unsafe, dynamic or unsupported projectDir mapping for "+alias+" in "+path+"; alias unresolved.");continue;}
    if(aliases.containsKey(alias))aliases.put(alias,join(root,value.group(1)));else warn(warnings,"projectDir mapping for undeclared project "+alias+" in "+path+" omitted.");
   }
   builds.add(new Build(root,Map.copyOf(aliases)));
   if(total>400)break;
  }
  builds.sort(Comparator.comparingInt((Build b)->b.root().length()).reversed());return builds;
 }
 private static boolean literalArguments(String args){
  Matcher literals=Pattern.compile("[\"'][:\\w.-]+[\"']").matcher(args);int end=0,count=0;
  while(literals.find()){String gap=args.substring(end,literals.start()).trim();if(count==0?!gap.isEmpty():!gap.equals(","))return false;end=literals.end();count++;}
  String tail=args.substring(end).trim();return count>0&&(tail.isEmpty()||tail.equals(","));
 }
 private static void warn(List<String> warnings,String message){if(warnings.size()<300)warnings.add(message);else if(warnings.size()==300)warnings.add("Additional Gradle settings warnings omitted.");}
 private static boolean validPath(String path){return !path.startsWith("/")&&!path.endsWith("/")&&!path.contains("//")&&Arrays.stream(path.split("/")).noneMatch(s->s.equals("..")||s.equals(".")||s.isEmpty());}
 private static String join(String root,String path){return root.equals(".")?path:root+"/"+path;}
 static String target(List<Build> builds,String modulePath,String alias,Map<String,String> fallback){
  for(Build build:builds)if(build.root.equals(".")||modulePath.equals(build.root)||modulePath.startsWith(build.root+"/")){String path=build.aliases.get(alias);return path==null?null:"gradle:"+path;}
  return fallback.get(alias);
 }
}

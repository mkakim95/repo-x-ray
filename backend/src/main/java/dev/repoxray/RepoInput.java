package dev.repoxray;
import java.net.URI;
public record RepoInput(String owner,String name,String ref) {
 public static RepoInput parse(String input,String ref) {
  try {
   if(input==null || input.length()>300) throw new IllegalArgumentException();
   URI u=URI.create(input.trim());
   if(!"https".equals(u.getScheme()) || !"github.com".equalsIgnoreCase(u.getHost()) || u.getPort()!=-1 || u.getUserInfo()!=null || u.getRawQuery()!=null || u.getRawFragment()!=null) throw new IllegalArgumentException();
   String[] p=u.getPath().replaceAll("/$", "").split("/");
   if(p.length!=3 || !p[1].matches("[A-Za-z0-9][A-Za-z0-9-]{0,38}") || !p[2].matches("[A-Za-z0-9_.-]{1,100}")) throw new IllegalArgumentException();
   String name=p[2].replaceFirst("\\.git$","");
   if(name.equals(".") || name.equals("..") || name.isEmpty()) throw new IllegalArgumentException();
   if(ref!=null && (!ref.matches("[A-Za-z0-9_./-]{1,200}") || ref.contains("..") || ref.startsWith("/"))) throw new IllegalArgumentException();
   return new RepoInput(p[1],name,ref);
  } catch(RuntimeException e) {throw new IllegalArgumentException("Use a public https://github.com/owner/repository URL and a valid optional ref.");}
 }
 public String url(){return "https://github.com/"+owner+"/"+name;}
}

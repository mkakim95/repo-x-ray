package dev.repoxray;
import java.net.URI;
/** The operator, not an API caller, chooses the one permitted GitLab authority. */
public record GitLabInput(String base,String project,String owner,String name,String ref) {
 public static String validateBase(String value) {
  if(value==null||value.isBlank())return null;
  if(value.length()>400)throw new IllegalArgumentException("GITLAB_BASE_URL is too long.");
  try {
   URI uri=URI.create(value);
   if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getRawQuery()!=null||uri.getRawFragment()!=null||uri.getRawPath().contains("%")||uri.getPath().contains("..")||!uri.getPath().matches("(/[A-Za-z0-9_-]+)*/?"))throw new IllegalArgumentException();
   if(uri.getPort()==0||uri.getPort()<-1||uri.getPort()>65535)throw new IllegalArgumentException();
   return value.replaceFirst("/$","");
  }catch(RuntimeException e){throw new IllegalArgumentException("GITLAB_BASE_URL must be a fixed HTTPS origin with an optional simple installation path; credentials, queries and fragments are forbidden.");}
 }
 public static GitLabInput parse(String input,String ref,String configuredBase) {
  try {
   String base=validateBase(configuredBase);if(base==null)throw new IllegalArgumentException();
   if(input==null||input.length()>600)throw new IllegalArgumentException();
   URI expected=URI.create(base),url=URI.create(input.trim());
   if(!"https".equals(url.getScheme())||url.getHost()==null||!url.getHost().equalsIgnoreCase(expected.getHost())||url.getPort()!=expected.getPort()||url.getUserInfo()!=null||url.getRawQuery()!=null||url.getRawFragment()!=null||url.getRawPath().contains("%"))throw new IllegalArgumentException();
   String prefix=expected.getPath()+"/",path=url.getPath();if(!path.startsWith(prefix))throw new IllegalArgumentException();
   String project=path.substring(prefix.length()).replaceFirst("/$","").replaceFirst("\\.git$","");String[] parts=project.split("/",-1);
   if(parts.length<2||parts.length>20)throw new IllegalArgumentException();
   for(String part:parts)if(!part.matches("[A-Za-z0-9_][A-Za-z0-9_.-]{0,99}")||part.equals(".")||part.equals(".."))throw new IllegalArgumentException();
   if(ref!=null&&(!ref.matches("[A-Za-z0-9_./-]{1,200}")||ref.contains("..")||ref.startsWith("/")))throw new IllegalArgumentException();
   return new GitLabInput(base,project,project.substring(0,project.lastIndexOf('/')),parts[parts.length-1],ref);
  }catch(RuntimeException e){throw new IllegalArgumentException("Use a public GitHub URL or a repository URL on the operator-configured HTTPS GitLab instance. Credentials, query parameters and repository subpages are not accepted.");}
 }
 public String url(){return base+"/"+project;}
}

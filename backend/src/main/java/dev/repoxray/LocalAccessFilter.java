package dev.repoxray;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.net.URI;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;
/** Local deployment only. Blocks DNS-rebinding Host and cross-site browser origins. Not user authentication. */
@Component
@Order(-100)
public final class LocalAccessFilter extends OncePerRequestFilter {
 private static final Set<String> HOSTS=Set.of("localhost","127.0.0.1","[::1]","::1");
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {
  response.setHeader("Cache-Control","no-store");response.setHeader("Pragma","no-cache");response.setHeader("Referrer-Policy","no-referrer");response.setHeader("X-Content-Type-Options","nosniff");
  boolean allowed=HOSTS.contains(request.getServerName().toLowerCase(java.util.Locale.ROOT));String origin=request.getHeader("Origin");
  if(origin!=null){try{URI uri=URI.create(origin);allowed&=Set.of("http","https").contains(uri.getScheme())&&uri.getHost()!=null&&HOSTS.contains(uri.getHost().toLowerCase(java.util.Locale.ROOT))&&uri.getUserInfo()==null&&uri.getRawQuery()==null&&uri.getRawFragment()==null&&(uri.getPath()==null||uri.getPath().isEmpty());}catch(RuntimeException e){allowed=false;}}
  if(!allowed){response.setStatus(403);response.setContentType("application/json");response.getWriter().write("{\"error\":\"LOCAL_ONLY\",\"message\":\"This deployment accepts localhost access only.\"}");return;}
  chain.doFilter(request,response);
 }
}

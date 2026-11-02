package dev.repoxray;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
/** Bound JSON body even when Content-Length is absent or dishonest. */
@Component
public final class RequestSizeFilter extends OncePerRequestFilter {
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {
  if(!request.getMethod().equals("POST")){chain.doFilter(request,response);return;}
  byte[] body=request.getInputStream().readNBytes(8193);
  if(body.length>8192){response.setStatus(413);response.setContentType("application/json");response.getWriter().write("{\"error\":\"BODY_TOO_LARGE\",\"message\":\"Request must not exceed 8 KiB.\"}");return;}
  chain.doFilter(new HttpServletRequestWrapper(request){
   @Override public ServletInputStream getInputStream(){ByteArrayInputStream in=new ByteArrayInputStream(body);return new ServletInputStream(){public int read(){return in.read();}public boolean isFinished(){return in.available()==0;}public boolean isReady(){return true;}public void setReadListener(ReadListener listener){throw new UnsupportedOperationException("Synchronous requests only");}};}
   @Override public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),java.nio.charset.StandardCharsets.UTF_8));}
  },response);
 }
}

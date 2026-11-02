package dev.repoxray;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;
class RequestSizeFilterTest {
 @Test void rejectsOversizedBodyIncludingPathParameters() throws Exception {
  for(String path:new String[]{"/api/analyze","/api/analyze;x=1"}) {
   var request=new MockHttpServletRequest("POST",path);request.setContent(("{\"unknown\":\""+"x".repeat(9000)+"\"}").getBytes(StandardCharsets.UTF_8));
   var response=new MockHttpServletResponse();boolean[] called={false};
   new RequestSizeFilter().doFilter(request,response,(r,s)->called[0]=true);
   assertEquals(413,response.getStatus());assertFalse(called[0]);
  }
 }
 @Test void preservesSmallBody() throws Exception {
  var request=new MockHttpServletRequest("POST","/api/analyze");byte[] body="{\"url\":\"https://github.com/a/b\"}".getBytes(StandardCharsets.UTF_8);request.setContent(body);
  new RequestSizeFilter().doFilter(request,new MockHttpServletResponse(),(r,s)->assertArrayEquals(body,r.getInputStream().readAllBytes()));
 }
}

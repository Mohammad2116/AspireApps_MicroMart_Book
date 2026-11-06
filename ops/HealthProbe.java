import java.net.URI;
import java.net.http.*;
import java.time.Duration;
public class HealthProbe {
 public static void main(String[] args) throws Exception {
  String port=System.getenv().getOrDefault("SERVER_PORT","8080");
  int status=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/actuator/health/readiness")).timeout(Duration.ofSeconds(3)).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode();
  if(status!=200)System.exit(1);
 }
}

package com.example.jbossrestart;

import org.springframework.stereotype.Service;
import java.net.http.*;
import java.net.URI;
import java.util.Base64;
import java.util.regex.*;

@Service
public class TsmStatusService {

    public String getTsmStatus() {
        try {
            String auth = "tafj.admin:AXI@gtpqrY4";
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());

            String requestBody = "{\"ofsRequest\":\"TSA.SERVICE,/S/PROCESS,MB.OFFICER/123123,TSM \"}";

            HttpClient client = HttpClient.newHttpClient();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8080/TAFJRestServices/resources/ofs"))
                    .header("Authorization", "Basic " + encodedAuth)
                    .header("Content-Type", "application/json")
                    .header("cache-control", "no-cache")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            String body = response.body();

            // Extract SERVICE.CONTROL status
            Pattern pattern = Pattern.compile("SERVICE\\.CONTROL:1:1=([A-Z]+)");
            Matcher matcher = pattern.matcher(body);

            if (matcher.find()) {
                return matcher.group(1); // e.g., START or STOP
            } else {
                return "UNKNOWN";
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR";
        }
    }
}

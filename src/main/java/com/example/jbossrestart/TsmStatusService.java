package com.example.jbossrestart;

import org.springframework.stereotype.Service;
import java.net.http.*;
import java.net.URI;
import java.util.Base64;
import java.util.regex.*;

@Service
public class TsmStatusService {

    private static final String TSM_URL = "http://localhost:8080/TAFJRestServices/resources/ofs";

    private static final String ENCODED_AUTH = "dGFmai5hZG1pbjpBWElAZ3RwcXJYNC==";

    public String getTsmStatus() {
        try {

            String requestBody = "{\"ofsRequest\":\"TSA.SERVICE,/S/PROCESS,MB.OFFICER/123123,TSM \"}";

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(TSM_URL))
                    .header("Authorization", "Basic " + ENCODED_AUTH)
                    .header("Content-Type", "application/json")
                    .header("Cache-Control", "no-cache")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            System.out.println(request.toString());

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("TSM Response: " + response.body());

            // Extract SERVICE.CONTROL
            Pattern pattern = Pattern.compile("SERVICE\\.CONTROL:[^=]*=([A-Z]+)");
            Matcher matcher = pattern.matcher(response.body());

            if (matcher.find()) {
                return matcher.group(1).trim();
            } else {
                return "UNKNOWN";
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR";
        }
    }

    public String startTsmService() {
        try {

            String requestBody = "{\"ofsRequest\":\"TSA.SERVICE,START/I/PROCESS,MB.OFFICER/123123,TSM \"}";

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(TSM_URL))
                    .header("Authorization", "Basic " + ENCODED_AUTH)
                    .header("Content-Type", "application/json")
                    .header("Cache-Control", "no-cache")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            System.out.println("TSM START REQUEST:\n" + requestBody);

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("TSM START RESPONSE:\n" + response.body());

            return response.body();
        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR";
        }
    }
    public String stopTsmService() {
        try {

            String requestBody = "{\"ofsRequest\":\"TSA.SERVICE,STOP/I/PROCESS,MB.OFFICER/123123,TSM \"}";

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(TSM_URL))
                    .header("Authorization", "Basic " + ENCODED_AUTH)
                    .header("Content-Type", "application/json")
                    .header("Cache-Control", "no-cache")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            System.out.println("TSM STOP REQUEST:\n" + requestBody);

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("TSM STOP RESPONSE:\n" + response.body());

            return response.body();
        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR";
        }
    }

    public String startTsmTafjjee() {
        try {
            String url = "http://localhost:8080/TAFJRestServices/resources/trun";

            String formData = "command=START.TSM+1&inParameters=param%3DOUT.PARAM";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Basic " + ENCODED_AUTH) // ✅ Using shared encoded string
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formData))
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            return response.body();
        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR: " + e.getMessage();
        }
    }

}

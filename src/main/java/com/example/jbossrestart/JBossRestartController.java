package com.example.jbossrestart;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

@Controller
public class JBossRestartController {

    // === JBoss config ===
    private static final String JBossHost = "localhost";
    private static final int JBossPort = 8080;
    private static final String JBossService = "jboss";

    // === TSM config ===
    private static final String TSM_URL = "http://localhost:8080/TAFJRestServices/resources/ofs";
    private static final String TSM_AUTH_HEADER = "Basic dGFmai5hZG1pbjpBWElAZ3RwcXJYNC=="; // base64 auth

    @GetMapping("/jboss-restart")
    public String showDashboard(Model model, HttpServletRequest request) {
        model.addAttribute("jbossStatus", checkJbossStatus());
        model.addAttribute("tsmStatus", getTsmStatus());
        model.addAttribute("jbossLog", request.getSession().getAttribute("jbossLog"));
        request.getSession().removeAttribute("jbossLog");
        return "jboss-restart";
    }

    @PostMapping("/jboss-restart")
    public String restartJboss(HttpServletRequest request) {
        StringBuilder output = new StringBuilder();

        output.append("🔴 Stopping JBoss...\n");
        String stopResult = executeCommand("sudo /bin/systemctl stop " + JBossService);
        output.append("✔️ JBoss successfully stopped.\n\n");

        output.append("🧹 Clearing TSA.STATUS data...\n");
        String dbtoolsResult = executeCommand("DBTools -u admin.dbtools -p uf@Ex5YHA -s JQL CLEAR-FILE F.TSA.STATUS");
        output.append("✔️ TSA.STATUS cleared successfully.\n\n");

        output.append("🟢 Starting JBoss...\n");
        String startResult = executeCommand("sudo /bin/systemctl start " + JBossService);
        output.append("✔️ JBoss started successfully.\n\n");

        request.getSession().setAttribute("jbossLog", output.toString());

        return "redirect:/jboss-restart";
    }

    private String checkJbossStatus() {
        return (isServiceActive() && isSocketOpen()) ? "Running"
                : isServiceActive() ? "Initializing"
                : "Stopped";
    }

    private boolean isServiceActive() {
        try {
            Process process = new ProcessBuilder("systemctl", "is-active", JBossService)
                    .redirectErrorStream(true)
                    .start();
            process.waitFor();
            String output = new String(process.getInputStream().readAllBytes()).trim();
            return "active".equals(output);
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    private boolean isSocketOpen() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(JBossHost, JBossPort), 1000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private String executeCommand(String command) {
        StringBuilder output = new StringBuilder();
        try {
            Process process = Runtime.getRuntime().exec(command);
            process.waitFor();
            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
        } catch (Exception e) {
            output.append("Error: ").append(e.getMessage());
        }
        return output.toString();
    }

    private String getTsmStatus() {
        try {
            RestTemplate restTemplate = new RestTemplate();

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", TSM_AUTH_HEADER);
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setCacheControl(CacheControl.noCache());

            String jsonBody = "{\"ofsRequest\":\"TSA.SERVICE,/S/PROCESS,MB.OFFICER/123123,TSM \"}";
            HttpEntity<String> requestEntity = new HttpEntity<>(jsonBody, headers);

            ResponseEntity<String> responseEntity = restTemplate.postForEntity(TSM_URL, requestEntity, String.class);

            if (responseEntity.getStatusCode() == HttpStatus.OK) {
                String body = responseEntity.getBody();

                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(body);

                String ofsResponse = root.path("ofsResponse").asText();
                ofsResponse = extractServiceControl(ofsResponse);

                if (ofsResponse == null) return "UNKNOWN";

                int equalsIndex = ofsResponse.indexOf('=');
                if (equalsIndex != -1 && equalsIndex + 1 < ofsResponse.length()) {
                    return ofsResponse.substring(equalsIndex + 1).trim();
                }
                return "UNKNOWN";
            } else {
                return "error: bad response";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "error";
        }
    }

    private String extractServiceControl(String ofsResponse) {
        if (ofsResponse == null) return null;

        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("SERVICE\\.CONTROL:([^,]*)");
        java.util.regex.Matcher matcher = pattern.matcher(ofsResponse);

        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }
}

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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Controller
public class JBossRestartController {

    // === JBoss config ===
    private static final String JBossHost = "localhost";
    private static final int JBossPort = 8080;
    private static final String JBossService = "jboss";

    // === TSM config ===
    private static final String TSM_URL = "http://localhost:8080/TAFJRestServices/resources/ofs";
    private static final String TSM_AUTH_HEADER = "Basic dGFmai5hZG1pbjpBWElAZ3RwcXJYNC==";

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
        executeCommand("sudo /bin/systemctl stop " + JBossService);
        output.append("✔️ JBoss successfully stopped.\n\n");

        output.append("🧹 Clearing TSA.STATUS data...\n");
        executeCommand("DBTools -u admin.dbtools -p uf@Ex5YHA -s JQL CLEAR-FILE F.TSA.STATUS");
        output.append("✔️ TSA.STATUS cleared successfully.\n\n");

        output.append("🟢 Starting JBoss...\n");
        executeCommand("sudo /bin/systemctl start " + JBossService);
        output.append("✔️ JBoss started successfully.\n\n");

        request.getSession().setAttribute("jbossLog", output.toString());
        return "redirect:/jboss-restart";
    }

    private String checkJbossStatus() {
        boolean active = isServiceActive();
        boolean portOpen = isSocketOpen();
        return active && portOpen ? "Running" : active ? "Initializing" : "Stopped";
    }

    private boolean isServiceActive() {
        try {
            Process process = new ProcessBuilder("systemctl", "is-active", JBossService)
                    .redirectErrorStream(true).start();
            process.waitFor();
            String output = new String(process.getInputStream().readAllBytes()).trim();
            return "active".equalsIgnoreCase(output);
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
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
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
                JsonNode root = new ObjectMapper().readTree(body);
                String ofsResponse = extractServiceControl(root.path("ofsResponse").asText());

                if (ofsResponse == null) return "UNKNOWN";

                int idx = ofsResponse.indexOf('=');
                return (idx != -1 && idx + 1 < ofsResponse.length())
                        ? ofsResponse.substring(idx + 1).trim()
                        : "UNKNOWN";
            } else {
                return "ERROR: bad response";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR";
        }
    }

    private String extractServiceControl(String ofsResponse) {
        if (ofsResponse == null) return null;
        Pattern pattern = Pattern.compile("SERVICE\\.CONTROL:([^,]*)");
        Matcher matcher = pattern.matcher(ofsResponse);
        return matcher.find() ? matcher.group(1).trim() : null;
    }
}

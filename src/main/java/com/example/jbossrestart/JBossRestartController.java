package com.example.jbossrestart;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

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
        model.addAttribute("tsmStatus", getTsmStatus(request));
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

    private String getTsmStatus(HttpServletRequest request) {
        String curlCommand = "curl -s --request POST " +
                "--url http://localhost:8080/TAFJRestServices/resources/ofs " +
                "--header \"Authorization: Basic dGFmai5hZG1pbjpBWElAZ3RwcXJYNC==\" " +
                "--header \"cache-control: no-cache\" " +
                "--header \"content-type: application/json\" " +
                "--data '{\"ofsRequest\":\"TSA.SERVICE,/S/PROCESS,MB.OFFICER/123123,TSM \"}'";

        String output = executeCommand(curlCommand);
        System.out.println(output);

        String extracted = extractServiceControl(output);

        StringBuilder log = new StringBuilder();

        log.append(output);
        request.getSession().setAttribute("jbossLog", log.toString());



        if (extracted == null) return "UNKNOWN";

        int equalsIndex = extracted.indexOf('=');
        if (equalsIndex != -1 && equalsIndex + 1 < extracted.length()) {
            return extracted.substring(equalsIndex + 1).trim();
        }

        return "UNKNOWN";

    }

    private String extractServiceControl(String ofsResponse) {
        if (ofsResponse == null) return null;
        Pattern pattern = Pattern.compile("SERVICE\\.CONTROL:([^,]*)");
        Matcher matcher = pattern.matcher(ofsResponse);
        return matcher.find() ? matcher.group(1).trim() : null;
    }
}

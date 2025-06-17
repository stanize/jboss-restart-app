package com.example.jbossrestart;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

@Controller
public class JBossRestartController {

    // Constants for JBoss service, host, and port
    private static final String JBossHost = "localhost";
    private static final int JBossPort = 8080;
    private static final String JBossService = "jboss";

    @GetMapping("/jboss-restart")
    public String showRestartPage(Model model, HttpServletRequest request) {
        String status = checkJbossStatus();
        model.addAttribute("jbossStatus", status);
        return "jboss-restart";
    }

    @PostMapping("/jboss-restart")
    public String restartJboss(HttpServletRequest request) {
        String output = executeCommand("sudo /bin/systemctl restart " + JBossService);
        request.getSession().setAttribute("jbossLog", "JBoss restart initiated.\n\n" + output);
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
}

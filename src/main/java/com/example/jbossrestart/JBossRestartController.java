package com.example.jbossrestart;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import javax.servlet.http.HttpServletRequest;

@Controller
public class JBossRestartController {

    @GetMapping("/jboss-restart")
    public String showRestartPage(Model model, HttpServletRequest request) {
        String status = getJbossStatus();
        model.addAttribute("jbossStatus", status);
        return "jboss-restart";
    }

    @PostMapping("/jboss-restart")
    public String restartJboss(HttpServletRequest request) {
        String output = executeCommand("sudo /bin/systemctl restart jboss");
        request.getSession().setAttribute("jbossLog", "JBoss restart initiated.\n\n" + output);
        return "redirect:/jboss-restart";
    }

    private String getJbossStatus() {
        String output = executeCommand("systemctl is-active jboss").trim();
        return output.equals("active") ? "Running" : "Stopped";
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

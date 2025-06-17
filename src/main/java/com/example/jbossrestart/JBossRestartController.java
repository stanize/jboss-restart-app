package com.example.jbossrestart;

import javax.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class JBossRestartController {

    @GetMapping("/")
    public String index() {
        return "jboss-restart";
    }

    @PostMapping("/restart")
    public String restartJboss(HttpServletRequest request) {
        String output = executeCommand("sudo /bin/systemctl restart jboss");
        request.getSession().setAttribute("jbossLog", output);
        return "redirect:/";
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

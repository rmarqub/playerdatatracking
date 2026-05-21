package com.playerdatatracking.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.logging.Logger;

@Component
public class PythonApiLauncher implements ApplicationListener<ContextRefreshedEvent> {

    private static final Logger LOG = Logger.getLogger(PythonApiLauncher.class.getName());

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    private volatile boolean launched = false;

    @Value("${predict.api.url:http://localhost:8001}")
    private String predictApiUrl;

    @Value("${valuation.api.url:http://localhost:8002}")
    private String valuationApiUrl;

    @Value("${python.api.dir:}")
    private String pythonApiDirOverride;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (launched) return;
        launched = true;

        File mlDir = resolveMlDir();
        if (mlDir == null) {
            LOG.warning("[PythonApiLauncher] No se encontró el directorio data-api/ml. Las APIs Python no se lanzarán automáticamente.");
            return;
        }

        File logsDir = new File(mlDir, "logs");
        logsDir.mkdirs();

        launchIfDown("predict_api", predictApiUrl, 8001, mlDir, logsDir);
        launchIfDown("valuation_api", valuationApiUrl, 8002, mlDir, logsDir);
    }

    private File resolveMlDir() {
        if (!pythonApiDirOverride.isBlank()) {
            File f = new File(pythonApiDirOverride);
            if (f.isDirectory()) return f;
            LOG.warning("[PythonApiLauncher] python.api.dir configurado pero no existe: " + pythonApiDirOverride);
        }

        String userDir = System.getProperty("user.dir");

        // Caso JAR: user.dir = raíz del proyecto
        File candidate = new File(userDir, "data-api/ml");
        if (candidate.isDirectory() && new File(candidate, "predict_api.py").exists()) {
            return candidate;
        }

        // Caso IDE: user.dir = backend/
        candidate = new File(userDir, "../data-api/ml");
        try {
            candidate = candidate.getCanonicalFile();
        } catch (Exception ignored) {}
        if (candidate.isDirectory() && new File(candidate, "predict_api.py").exists()) {
            return candidate;
        }

        return null;
    }

    private void launchIfDown(String module, String baseUrl, int port, File mlDir, File logsDir) {
        if (isAlive(baseUrl + "/health")) {
            LOG.info("[PythonApiLauncher] " + module + " ya está activo en " + baseUrl);
            return;
        }

        LOG.info("[PythonApiLauncher] Lanzando " + module + " en puerto " + port + "...");
        try {
            File logFile = new File(logsDir, module + ".log");
            ProcessBuilder pb = new ProcessBuilder(
                    List.of("python", "-m", "uvicorn",
                            module + ":app",
                            "--host", "127.0.0.1",
                            "--port", String.valueOf(port))
            );
            pb.directory(mlDir);
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));
            pb.start();
            LOG.info("[PythonApiLauncher] " + module + " iniciado. Log: " + logFile.getAbsolutePath());
        } catch (Exception e) {
            LOG.warning("[PythonApiLauncher] No se pudo lanzar " + module + ": " + e.getMessage());
        }
    }

    private boolean isAlive(String healthUrl) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(healthUrl))
                    .GET()
                    .timeout(Duration.ofSeconds(2))
                    .build();
            HttpResponse<Void> res = HTTP.send(req, HttpResponse.BodyHandlers.discarding());
            return res.statusCode() >= 200 && res.statusCode() < 300;
        } catch (Exception e) {
            return false;
        }
    }
}

package com.playerdatatracking.services;

import java.io.File;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class PredictApiProcessManager implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(PredictApiProcessManager.class);

    @Value("${predict.api.python:python}")
    private String pythonCmd;

    @Value("${predict.api.workdir:../data-api/ml}")
    private String workdir;

    @Value("${predict.api.autostart:true}")
    private boolean autostart;

    private Process process;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!autostart) {
            log.info("FastAPI auto-start disabled (predict.api.autostart=false)");
            return;
        }

        File dir = new File(workdir);
        if (!dir.exists()) {
            log.warn("FastAPI workdir not found: {} — skipping auto-start", dir.getAbsolutePath());
            return;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(
                pythonCmd, "-m", "uvicorn", "predict_api:app",
                "--host", "0.0.0.0", "--port", "8001"
            );
            pb.directory(dir);
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);

            process = pb.start();
            log.info("FastAPI predict service started (PID {}, workdir: {})",
                process.pid(), dir.getAbsolutePath());

        } catch (Exception e) {
            log.warn("Could not auto-start FastAPI predict service: {}", e.getMessage());
        }
    }

    @Override
    public void destroy() {
        if (process == null || !process.isAlive()) return;
        try {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
            log.info("FastAPI predict service stopped");
        } catch (Exception e) {
            log.warn("Error stopping FastAPI process: {}", e.getMessage());
        }
    }
}

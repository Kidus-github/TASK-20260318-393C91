package com.citybus.platform.application;

import com.citybus.platform.domain.FailedMessage;
import com.citybus.platform.domain.Message;
import com.citybus.platform.domain.QueueStatus;
import com.citybus.platform.domain.QueuedMessage;
import com.citybus.platform.infrastructure.persistence.FailedMessageRepository;
import com.citybus.platform.infrastructure.persistence.MessageRepository;
import com.citybus.platform.infrastructure.persistence.QueuedMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@Service
public class QueueService {
    private static final Logger LOGGER = LoggerFactory.getLogger(QueueService.class);

    private final QueuedMessageRepository queuedMessageRepository;
    private final MessageRepository messageRepository;
    private final FailedMessageRepository failedMessageRepository;
    private final AppProperties appProperties;
    private final AlertService alertService;
    private final DiagnosticReportService diagnosticReportService;

    public QueueService(
            QueuedMessageRepository queuedMessageRepository,
            MessageRepository messageRepository,
            FailedMessageRepository failedMessageRepository,
            AppProperties appProperties,
            AlertService alertService,
            DiagnosticReportService diagnosticReportService
    ) {
        this.queuedMessageRepository = queuedMessageRepository;
        this.messageRepository = messageRepository;
        this.failedMessageRepository = failedMessageRepository;
        this.appProperties = appProperties;
        this.alertService = alertService;
        this.diagnosticReportService = diagnosticReportService;
    }

    @Scheduled(fixedDelay = 10000)
    @Transactional
    public void consumeQueue() {
        long backlog = queuedMessageRepository.countByStatus(QueueStatus.PENDING);
        if (backlog > appProperties.alerts().queueBacklogThreshold()) {
            alertService.createAlert("QUEUE_BACKLOG", "WARN", "Queue backlog threshold exceeded",
                    "Pending queue depth is " + backlog, MDC.get("traceId"));
        }
        List<QueuedMessage> dueMessages = queuedMessageRepository.findTop50ByStatusAndScheduledAtBeforeOrderByScheduledAtAsc(QueueStatus.PENDING, Instant.now());
        if (!dueMessages.isEmpty()) {
            long p95LatencyMs = queueP95LatencyMs(dueMessages);
            if (p95LatencyMs > appProperties.alerts().apiP95ThresholdMs()) {
                HashMap<String, Object> summary = new HashMap<>();
                summary.put("p95LatencyMs", p95LatencyMs);
                summary.put("thresholdMs", appProperties.alerts().apiP95ThresholdMs());
                summary.put("sampleSize", dueMessages.size());
                var report = diagnosticReportService.createReport("QUEUE_LATENCY", summary, MDC.get("traceId"));
                alertService.createAlert("QUEUE_LATENCY", "WARN", "Queue latency threshold exceeded",
                        "Queue P95 delay is " + p95LatencyMs + "ms (reportId=" + report.getId() + ")", MDC.get("traceId"));
            }
        }
        dueMessages.forEach(this::process);
    }

    private void process(QueuedMessage queuedMessage) {
        try {
            queuedMessage.setStatus(QueueStatus.PROCESSING);
            queuedMessageRepository.save(queuedMessage);

            Message message = new Message();
            message.setId(UUID.randomUUID());
            message.setUserId(queuedMessage.getUserId());
            message.setType(queuedMessage.getMessageType());
            message.setTitle(queuedMessage.getTitle());
            message.setContent(queuedMessage.getContent());
            message.setSensitivityLevel(queuedMessage.getSensitivityLevel());
            message.setRead(false);
            message.setCreatedAt(Instant.now());
            messageRepository.save(message);

            queuedMessage.setStatus(QueueStatus.DELIVERED);
            queuedMessageRepository.save(queuedMessage);
        } catch (Exception exception) {
            queuedMessage.setRetryCount(queuedMessage.getRetryCount() + 1);
            if (queuedMessage.getRetryCount() >= appProperties.queue().maxRetries()) {
                queuedMessage.setStatus(QueueStatus.DEAD_LETTERED);
                FailedMessage failedMessage = new FailedMessage();
                failedMessage.setId(UUID.randomUUID());
                failedMessage.setOriginalQueueId(queuedMessage.getId());
                failedMessage.setErrorSummary(exception.getMessage() == null ? "Unknown queue processing error" : exception.getMessage());
                failedMessage.setPayloadHash(Integer.toHexString(queuedMessage.getPayload().hashCode()));
                failedMessage.setTraceId(queuedMessage.getTraceId());
                failedMessage.setCreatedAt(Instant.now());
                failedMessageRepository.save(failedMessage);
            } else {
                queuedMessage.setStatus(QueueStatus.PENDING);
            }
            queuedMessageRepository.save(queuedMessage);
            LOGGER.error("Queue processing failed for message {}", queuedMessage.getId(), exception);
        }
    }

    private long queueP95LatencyMs(List<QueuedMessage> dueMessages) {
        List<Long> latencies = dueMessages.stream()
                .map(item -> Math.max(0L, Instant.now().toEpochMilli() - item.getScheduledAt().toEpochMilli()))
                .sorted()
                .toList();
        int index = Math.max(0, (int) Math.ceil(latencies.size() * 0.95) - 1);
        return latencies.get(index);
    }
}

package com.clipperai.product.service.billing;


import com.clipperai.product.entity.billing.StripeWebhookEvent;
import com.clipperai.product.repository.StripeWebhookEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class StripeWebhookEventService {

    private final StripeWebhookEventRepository stripeWebhookEventRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StripeWebhookEvent recordReceivedOrGetExisting(
            String stripeEventId,
            String eventType,
            String stripeObjectId
    ) {
        return stripeWebhookEventRepository.findByStripeEventId(stripeEventId)
                .orElseGet(() -> {
                    StripeWebhookEvent webhookEvent = StripeWebhookEvent.builder()
                            .stripeEventId(stripeEventId)
                            .eventType(eventType)
                            .stripeObjectId(stripeObjectId)
                            .status("received")
                            .receivedAt(OffsetDateTime.now())
                            .build();

                    try {
                        return stripeWebhookEventRepository.saveAndFlush(webhookEvent);
                    } catch (DataIntegrityViolationException duplicateRace) {
                        return stripeWebhookEventRepository.findByStripeEventId(stripeEventId)
                                .orElseThrow(() -> duplicateRace);
                    }
                });
    }

    public boolean alreadyProcessed(StripeWebhookEvent webhookEvent) {
        return "processed".equalsIgnoreCase(webhookEvent.getStatus());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessed(String stripeEventId) {
        StripeWebhookEvent webhookEvent = stripeWebhookEventRepository.findByStripeEventId(stripeEventId)
                .orElseThrow(() -> new IllegalStateException(
                        "Stripe webhook event not found: " + stripeEventId
                ));

        webhookEvent.setStatus("processed");
        webhookEvent.setProcessedAt(OffsetDateTime.now());
        webhookEvent.setErrorMessage(null);

        stripeWebhookEventRepository.save(webhookEvent);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String stripeEventId, Exception exception) {
        StripeWebhookEvent webhookEvent = stripeWebhookEventRepository.findByStripeEventId(stripeEventId)
                .orElseThrow(() -> new IllegalStateException(
                        "Stripe webhook event not found: " + stripeEventId
                ));

        webhookEvent.setStatus("failed");
        webhookEvent.setErrorMessage(shorten(exception.getMessage()));

        stripeWebhookEventRepository.save(webhookEvent);
    }

    private String shorten(String message) {
        if (message == null) {
            return null;
        }

        return message.length() > 1000
                ? message.substring(0, 1000)
                : message;
    }
}

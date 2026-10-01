package com.zentrox.forge.billing;

import com.zentrox.forge.entity.BillingWebhookEvent;
import com.zentrox.forge.repository.BillingWebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Claims a webhook event id, exactly once.
 *
 * <p>Separated from {@link BillingWebhookService} for one specific reason: the duplicate detection
 * works by letting the primary key reject the second insert, and <b>a constraint violation marks its
 * transaction rollback-only</b>. If that happened inside the service's transaction, catching the
 * exception would not save it - the subsequent commit would fail with
 * {@code UnexpectedRollbackException}, turning a harmless replay into a 500 and a provider retry
 * loop.
 *
 * <p>{@code REQUIRES_NEW} confines the violation to this suspended inner transaction, leaving the
 * caller's transaction untouched.
 *
 * <p><b>The exception is deliberately NOT caught here.</b> Catching it inside this method does not
 * help: the transaction is already marked rollback-only by then, and the commit that happens
 * afterwards at the proxy boundary throws {@code UnexpectedRollbackException} regardless. The catch
 * therefore has to live outside the transaction boundary, in the caller - see
 * {@link BillingWebhookService#handle}.
 *
 * <p>Why not {@code existsById} first? That is a check-then-act race: two concurrent deliveries of
 * the same event would both see "absent" and both proceed. The unique constraint is the only
 * mechanism that is actually atomic, so it is the one that decides. The pre-check would merely be an
 * optimisation, and this path is not hot.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookEventLedger {

    private final BillingWebhookEventRepository eventRepository;

    /**
     * Records the event id, committing in its own transaction so the claim survives even if the
     * caller's processing later fails - a replayed event must not be reprocessed just because the
     * first attempt errored downstream.
     *
     * @throws DataIntegrityViolationException if this event was already claimed. The caller must
     *         catch this; see the class javadoc for why it cannot be caught here.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void claim(String provider, String eventId, String eventType, String payload) {
        eventRepository.saveAndFlush(BillingWebhookEvent.builder()
                .provider(provider)
                .eventId(eventId)
                .eventType(eventType)
                .payload(payload)
                .receivedAt(Instant.now())
                .build());
    }
}

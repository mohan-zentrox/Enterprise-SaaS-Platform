package com.zentrox.forge.notification.event;

import com.zentrox.forge.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Turns domain events into notifications (FRD-10.3).
 *
 * The indirection is the point. Without it, WorkflowService and UserManagementService would call
 * NotificationService directly, and every future channel (Slack, webhooks, digests) would mean
 * editing business logic. They publish a fact; this class decides who hears about it.
 *
 * {@link TransactionalEventListener} with {@code AFTER_COMMIT} rather than a plain
 * {@code @EventListener}: a notification about a workflow transition that then rolled back would be
 * a lie. Nothing is sent until the originating transaction has actually committed.
 *
 * {@code @Async} keeps delivery (including SMTP) off the request thread. Note the consequence:
 * because the listener runs on another thread, TenantContext is empty there - which is why every
 * event carries its tenantId and NotificationService's write methods take it as a parameter.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserInvited(UserInvitedEvent event) {
        String title = "Welcome to " + event.organizationName();
        String body = """
                Hi %s,

                An administrator has created an account for you in %s with the %s role.

                Sign in with your email address (%s) and the initial password you were given, and
                change that password once you are in.""".formatted(
                event.fullName(), event.organizationName(), event.roleName(), event.email());

        notificationService.notifyInApp(event.tenantId(), event.userId(), "USER_INVITED", title, body, null);
        notificationService.notifyByEmail(event.email(), title, body);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWorkflowTransitioned(WorkflowTransitionedEvent event) {
        // Notifies the actor only. Notifying watchers/assignees is the natural next step, but it
        // needs a subscription model that does not exist yet - inventing one implicitly here (e.g.
        // "everyone in the tenant") would make every transition spam every user.
        if (event.actorUserId() == null) {
            return;
        }
        String title = "%s moved to %s".formatted(event.workflowName(), event.toState());
        String body = "The workflow '%s' moved from %s to %s.".formatted(
                event.workflowName(), event.fromState(), event.toState());
        String payload = """
                {"entityType":"WorkflowInstance","entityId":"%s","fromState":"%s","toState":"%s"}"""
                .formatted(event.instanceId(), event.fromState(), event.toState());

        notificationService.notifyInApp(
                event.tenantId(), event.actorUserId(), "WORKFLOW_TRANSITIONED", title, body, payload);
    }
}

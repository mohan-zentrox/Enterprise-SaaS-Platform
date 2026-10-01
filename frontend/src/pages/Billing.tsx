import { useEffect, useState } from "react";
import { NavBar } from "@/components/NavBar";
import { changePlan, getSubscription } from "@/api/billing";
import { describeApiError } from "@/api/errors";
import type { Subscription, SubscriptionPlan, Usage } from "@/types";

const PLANS: { plan: SubscriptionPlan; blurb: string }[] = [
  { plan: "FREE", blurb: "3 seats · 2 workflows · 100 instances/month" },
  { plan: "STARTER", blurb: "10 seats · 10 workflows · 1,000 instances/month" },
  { plan: "PROFESSIONAL", blurb: "50 seats · 100 workflows · 25,000 instances/month" },
  { plan: "ENTERPRISE", blurb: "Unlimited everything" },
];

const METRIC_LABELS: Record<Usage["metric"], string> = {
  SEATS: "People",
  WORKFLOW_DEFINITIONS: "Workflows",
  WORKFLOW_INSTANCES_PER_MONTH: "Instances this month",
  API_KEYS: "API keys",
};

/**
 * Plan and usage. The API returns both together, so this renders exactly what the server will
 * enforce rather than a client-side copy of the plan limits.
 */
export function Billing() {
  const [subscription, setSubscription] = useState<Subscription | null>(null);
  const [loading, setLoading] = useState(true);
  const [changing, setChanging] = useState<SubscriptionPlan | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  async function refresh() {
    setLoading(true);
    try {
      setSubscription(await getSubscription());
      setError(null);
    } catch (err) {
      setError(describeApiError(err, "Could not load your subscription. This page needs TENANT_READ."));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void refresh();
  }, []);

  async function handleChangePlan(plan: SubscriptionPlan) {
    setChanging(plan);
    setError(null);
    setNotice(null);
    try {
      setSubscription(await changePlan(plan));
      setNotice(`Moved to the ${plan} plan.`);
    } catch (err) {
      // The server's message is the useful part here - it names which limit blocks a downgrade, or
      // says plan changes are managed by the payment provider for this deployment.
      setError(describeApiError(err, "Could not change the plan."));
    } finally {
      setChanging(null);
    }
  }

  return (
    <div className="min-h-screen bg-slate-50">
      <NavBar />
      <main className="mx-auto max-w-4xl space-y-6 p-6">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Plan &amp; usage</h1>
          <p className="mt-1 text-sm text-slate-500">
            Limits are enforced server-side on every write. Exceeding one returns a clear error rather
            than failing silently.
          </p>
        </div>

        {error && <p className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}
        {notice && <p className="rounded-md bg-forge-50 px-3 py-2 text-sm text-forge-700">{notice}</p>}

        {loading && !subscription ? (
          <p className="text-sm text-slate-500">Loading…</p>
        ) : subscription ? (
          <>
            <section className="rounded-xl bg-white p-6 shadow-sm">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500">
                    Current plan
                  </h2>
                  <p className="mt-1 text-xl font-semibold text-slate-900">{subscription.plan}</p>
                </div>
                <StatusBadge status={subscription.status} />
              </div>

              {subscription.status === "PAST_DUE" && (
                <p className="mt-4 rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-800">
                  Payment failed. New changes are blocked until billing is updated — all of your
                  existing data stays readable.
                </p>
              )}
              {subscription.cancelAtPeriodEnd && subscription.currentPeriodEnd && (
                <p className="mt-4 rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-800">
                  Scheduled to cancel on {new Date(subscription.currentPeriodEnd).toLocaleDateString()}.
                </p>
              )}
            </section>

            <section className="rounded-xl bg-white p-6 shadow-sm">
              <h2 className="mb-4 text-sm font-semibold uppercase tracking-wide text-slate-500">
                Usage
              </h2>
              <div className="space-y-4">
                {subscription.usage.map((u) => (
                  <UsageBar key={u.metric} usage={u} />
                ))}
              </div>
            </section>

            <section className="rounded-xl bg-white p-6 shadow-sm">
              <h2 className="mb-4 text-sm font-semibold uppercase tracking-wide text-slate-500">
                Change plan
              </h2>
              <div className="grid gap-3 sm:grid-cols-2">
                {PLANS.map(({ plan, blurb }) => {
                  const current = plan === subscription.plan;
                  return (
                    <div
                      key={plan}
                      className={`rounded-lg border p-4 ${
                        current ? "border-forge-500 bg-forge-50" : "border-slate-200"
                      }`}
                    >
                      <div className="flex items-center justify-between gap-2">
                        <span className="font-medium text-slate-900">{plan}</span>
                        {current && (
                          <span className="rounded bg-forge-600 px-2 py-0.5 text-xs font-medium text-white">
                            Current
                          </span>
                        )}
                      </div>
                      <p className="mt-1 text-xs text-slate-500">{blurb}</p>
                      {!current && (
                        <button
                          disabled={changing !== null}
                          onClick={() => void handleChangePlan(plan)}
                          className="mt-3 rounded-md bg-forge-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-forge-700 disabled:opacity-50"
                        >
                          {changing === plan ? "Changing…" : `Switch to ${plan}`}
                        </button>
                      )}
                    </div>
                  );
                })}
              </div>
              <p className="mt-4 text-xs text-slate-400">
                Downgrades are refused while you are over the target plan's limits — reduce usage
                first. On deployments where a payment provider is connected, plan changes happen in
                the billing portal instead.
              </p>
            </section>
          </>
        ) : null}
      </main>
    </div>
  );
}

function StatusBadge({ status }: { status: Subscription["status"] }) {
  const styles: Record<Subscription["status"], string> = {
    ACTIVE: "bg-emerald-50 text-emerald-700",
    TRIALING: "bg-sky-50 text-sky-700",
    PAST_DUE: "bg-amber-50 text-amber-800",
    CANCELED: "bg-slate-100 text-slate-600",
  };
  const labels: Record<Subscription["status"], string> = {
    ACTIVE: "Active",
    TRIALING: "Trial",
    PAST_DUE: "Past due",
    CANCELED: "Cancelled",
  };
  return (
    <span className={`rounded px-2 py-1 text-xs font-medium ${styles[status]}`}>{labels[status]}</span>
  );
}

function UsageBar({ usage }: { usage: Usage }) {
  const pct = usage.unlimited || !usage.limit ? 0 : Math.min((usage.used / usage.limit) * 100, 100);
  // Colour by proximity to the limit, so approaching one is visible before it blocks a write.
  const barColour = pct >= 100 ? "bg-red-500" : pct >= 80 ? "bg-amber-500" : "bg-forge-500";

  return (
    <div>
      <div className="flex items-baseline justify-between text-sm">
        <span className="font-medium text-slate-700">{METRIC_LABELS[usage.metric]}</span>
        <span className="text-slate-500">
          {usage.used} {usage.unlimited ? "· unlimited" : `/ ${usage.limit}`}
        </span>
      </div>
      {!usage.unlimited && (
        <div className="mt-1 h-2 overflow-hidden rounded-full bg-slate-100">
          <div className={`h-full rounded-full ${barColour}`} style={{ width: `${pct}%` }} />
        </div>
      )}
      {pct >= 100 && (
        <p className="mt-1 text-xs text-red-600">
          At the limit — further additions are rejected until you upgrade.
        </p>
      )}
    </div>
  );
}

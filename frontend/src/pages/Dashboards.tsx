import { FormEvent, useEffect, useState } from "react";
import { NavBar } from "@/components/NavBar";
import {
  createDashboard,
  deleteDashboard,
  getDashboard,
  listDashboards,
} from "@/api/dashboards";
import { describeApiError } from "@/api/errors";
import type { Dashboard, Widget, WidgetType } from "@/types";

const WIDGET_LABELS: Record<WidgetType, string> = {
  WORKFLOW_DEFINITION_COUNT: "Workflow count",
  WORKFLOW_INSTANCE_COUNT: "Instance count",
  INSTANCES_BY_STATE: "Instances by state",
  ACTIVE_USER_COUNT: "Active people",
  RECENT_AUDIT_ACTIVITY: "Recent activity",
  SUBSCRIPTION_USAGE: "Plan & usage",
};

const ALL_WIDGETS = Object.keys(WIDGET_LABELS) as WidgetType[];

export function Dashboards() {
  const [dashboards, setDashboards] = useState<Dashboard[]>([]);
  const [selected, setSelected] = useState<Dashboard | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);

  const [name, setName] = useState("");
  const [shared, setShared] = useState(false);
  const [chosen, setChosen] = useState<Set<WidgetType>>(
    new Set(["WORKFLOW_INSTANCE_COUNT", "INSTANCES_BY_STATE"]),
  );

  async function refresh(selectId?: string) {
    setLoading(true);
    try {
      const list = await listDashboards();
      setDashboards(list);
      // The list response deliberately omits widget data; open one to have it resolved.
      const target = selectId ?? selected?.id ?? list[0]?.id;
      setSelected(target ? await getDashboard(target) : null);
      setError(null);
    } catch (err) {
      setError(describeApiError(err, "Could not load dashboards."));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void refresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function open(id: string) {
    try {
      setSelected(await getDashboard(id));
      setError(null);
    } catch (err) {
      setError(describeApiError(err, "Could not open that dashboard."));
    }
  }

  async function handleCreate(event: FormEvent) {
    event.preventDefault();
    setError(null);
    try {
      const created = await createDashboard({
        name,
        shared,
        widgets: [...chosen].map((widgetType, index) => ({
          widgetType,
          title: WIDGET_LABELS[widgetType],
          position: index,
        })),
      });
      setName("");
      setCreating(false);
      await refresh(created.id);
    } catch (err) {
      // Covers the TENANT_UPDATE requirement on shared dashboards and duplicate-name conflicts.
      setError(describeApiError(err, "Could not create the dashboard."));
    }
  }

  async function handleDelete(dashboard: Dashboard) {
    setError(null);
    try {
      await deleteDashboard(dashboard.id);
      setSelected(null);
      await refresh();
    } catch (err) {
      setError(describeApiError(err, "Could not delete that dashboard."));
    }
  }

  function toggleWidget(widget: WidgetType) {
    setChosen((prev) => {
      const next = new Set(prev);
      if (next.has(widget)) next.delete(widget);
      else next.add(widget);
      return next;
    });
  }

  return (
    <div className="min-h-screen bg-slate-50">
      <NavBar />
      <main className="mx-auto max-w-5xl space-y-6 p-6">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h1 className="text-2xl font-semibold text-slate-900">Dashboards</h1>
            <p className="mt-1 text-sm text-slate-500">
              Saved views over data you can already see. Figures are computed fresh on every open.
            </p>
          </div>
          <button
            onClick={() => setCreating((c) => !c)}
            className="rounded-md bg-forge-600 px-4 py-2 text-sm font-medium text-white hover:bg-forge-700"
          >
            {creating ? "Cancel" : "New dashboard"}
          </button>
        </div>

        {error && <p className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}

        {creating && (
          <section className="rounded-xl bg-white p-6 shadow-sm">
            <form onSubmit={handleCreate} className="space-y-4">
              <label className="block max-w-sm text-sm">
                <span className="mb-1 block font-medium text-slate-700">Name</span>
                <input required className="input" value={name} onChange={(e) => setName(e.target.value)} />
              </label>

              <label className="flex items-start gap-2 text-sm text-slate-600">
                <input
                  type="checkbox"
                  className="mt-0.5 rounded border-slate-300 text-forge-600 focus:ring-forge-500"
                  checked={shared}
                  onChange={(e) => setShared(e.target.checked)}
                />
                <span>
                  Share with the whole organization
                  <span className="block text-xs text-slate-500">
                    Requires the TENANT_UPDATE permission; otherwise the dashboard is private to you.
                  </span>
                </span>
              </label>

              <fieldset>
                <legend className="mb-2 text-sm font-medium text-slate-700">Widgets</legend>
                <div className="grid gap-2 sm:grid-cols-3">
                  {ALL_WIDGETS.map((widget) => (
                    <label key={widget} className="flex items-center gap-2 text-sm text-slate-600">
                      <input
                        type="checkbox"
                        className="rounded border-slate-300 text-forge-600 focus:ring-forge-500"
                        checked={chosen.has(widget)}
                        onChange={() => toggleWidget(widget)}
                      />
                      {WIDGET_LABELS[widget]}
                    </label>
                  ))}
                </div>
              </fieldset>

              <button
                type="submit"
                className="rounded-md bg-forge-600 px-4 py-2 text-sm font-medium text-white hover:bg-forge-700"
              >
                Create
              </button>
            </form>
          </section>
        )}

        {dashboards.length > 0 && (
          <div className="flex flex-wrap gap-2">
            {dashboards.map((d) => (
              <button
                key={d.id}
                onClick={() => void open(d.id)}
                className={`rounded-md px-3 py-1.5 text-sm font-medium ${
                  selected?.id === d.id
                    ? "bg-forge-600 text-white"
                    : "border border-slate-300 bg-white text-slate-700 hover:bg-slate-50"
                }`}
              >
                {d.name}
                {d.shared && <span className="ml-1 text-xs opacity-70">· shared</span>}
              </button>
            ))}
          </div>
        )}

        {loading && !selected ? (
          <p className="text-sm text-slate-500">Loading…</p>
        ) : !selected ? (
          <div className="rounded-xl bg-white p-8 text-center shadow-sm">
            <p className="text-sm text-slate-500">
              No dashboards yet. Create one to see counts, workflow states and plan usage at a glance.
            </p>
          </div>
        ) : (
          <>
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {selected.widgets.map((widget) => (
                <WidgetCard key={widget.id} widget={widget} />
              ))}
            </div>
            <button
              onClick={() => void handleDelete(selected)}
              className="rounded-md border border-red-200 px-3 py-1.5 text-xs font-medium text-red-700 hover:bg-red-50"
            >
              Delete “{selected.name}”
            </button>
          </>
        )}
      </main>
    </div>
  );
}

/**
 * Widget `data` is typed as unknown because its shape depends on `widgetType`, so each branch
 * narrows it explicitly rather than casting once and hoping.
 */
function WidgetCard({ widget }: { widget: Widget }) {
  return (
    <div className="rounded-xl bg-white p-5 shadow-sm">
      <h3 className="text-xs font-semibold uppercase tracking-wide text-slate-500">
        {widget.title ?? WIDGET_LABELS[widget.widgetType]}
      </h3>
      <div className="mt-3">{renderData(widget)}</div>
    </div>
  );
}

function renderData(widget: Widget) {
  const data = widget.data;

  if (isCount(data)) {
    return <p className="text-3xl font-semibold text-slate-900">{data.count}</p>;
  }

  if (widget.widgetType === "SUBSCRIPTION_USAGE" && isSubscriptionUsage(data)) {
    return (
      <div className="space-y-1 text-sm">
        <p className="font-medium text-slate-900">
          {data.plan} <span className="text-xs font-normal text-slate-500">({data.status})</span>
        </p>
        {data.usage.map((u) => (
          <p key={u.metric} className="text-xs text-slate-500">
            {u.metric.toLowerCase().replace(/_/g, " ")}: {u.used}
            {u.unlimited ? " / unlimited" : ` / ${u.limit}`}
          </p>
        ))}
      </div>
    );
  }

  if (widget.widgetType === "RECENT_AUDIT_ACTIVITY" && Array.isArray(data)) {
    if (data.length === 0) {
      return <p className="text-sm text-slate-500">No activity yet.</p>;
    }
    return (
      <ul className="space-y-1 text-xs text-slate-600">
        {(data as { action: string; entityType: string; occurredAt: string }[]).map((row, i) => (
          <li key={i} className="truncate">
            <span className="font-mono">{row.action}</span>{" "}
            <span className="text-slate-400">{new Date(row.occurredAt).toLocaleTimeString()}</span>
          </li>
        ))}
      </ul>
    );
  }

  if (widget.widgetType === "INSTANCES_BY_STATE" && isStringNumberMap(data)) {
    const entries = Object.entries(data);
    if (entries.length === 0) {
      return <p className="text-sm text-slate-500">No instances yet.</p>;
    }
    return (
      <ul className="space-y-1 text-sm">
        {entries.map(([state, count]) => (
          <li key={state} className="flex justify-between">
            <span className="font-mono text-xs text-slate-600">{state}</span>
            <span className="font-medium text-slate-900">{count}</span>
          </li>
        ))}
      </ul>
    );
  }

  return <pre className="overflow-x-auto text-xs text-slate-500">{JSON.stringify(data, null, 2)}</pre>;
}

function isCount(value: unknown): value is { count: number } {
  return (
    typeof value === "object" &&
    value !== null &&
    "count" in value &&
    typeof (value as { count: unknown }).count === "number"
  );
}

function isStringNumberMap(value: unknown): value is Record<string, number> {
  return (
    typeof value === "object" &&
    value !== null &&
    !Array.isArray(value) &&
    Object.values(value).every((v) => typeof v === "number")
  );
}

function isSubscriptionUsage(value: unknown): value is {
  plan: string;
  status: string;
  usage: { metric: string; used: number; limit: number | null; unlimited: boolean }[];
} {
  return (
    typeof value === "object" &&
    value !== null &&
    "plan" in value &&
    "usage" in value &&
    Array.isArray((value as { usage: unknown }).usage)
  );
}

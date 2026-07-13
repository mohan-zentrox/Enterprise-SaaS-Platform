import { FormEvent, useEffect, useState } from "react";
import { NavBar } from "@/components/NavBar";
import {
  createWorkflowDefinition,
  createWorkflowInstance,
  deleteWorkflowDefinition,
  listWorkflowDefinitions,
  listWorkflowInstances,
  transitionWorkflowInstance,
} from "@/api/workflows";
import type { WorkflowDefinition, WorkflowInstance } from "@/types";

export function Workflows() {
  const [definitions, setDefinitions] = useState<WorkflowDefinition[]>([]);
  const [instances, setInstances] = useState<WorkflowInstance[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const [name, setName] = useState("");
  const [statesInput, setStatesInput] = useState("DRAFT, IN_REVIEW, APPROVED, REJECTED");
  const [transitionsInput, setTransitionsInput] = useState(
    "DRAFT->IN_REVIEW, IN_REVIEW->APPROVED, IN_REVIEW->REJECTED",
  );

  async function refresh() {
    setLoading(true);
    try {
      const [defs, insts] = await Promise.all([listWorkflowDefinitions(), listWorkflowInstances()]);
      setDefinitions(defs);
      setInstances(insts);
      setError(null);
    } catch {
      setError("Failed to load workflows. Do you have WORKFLOW_DEFINITION_READ permission?");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void refresh();
  }, []);

  async function handleCreateDefinition(event: FormEvent) {
    event.preventDefault();
    const states = statesInput
      .split(",")
      .map((s) => s.trim())
      .filter(Boolean);
    const transitions = transitionsInput
      .split(",")
      .map((pair) => pair.trim())
      .filter(Boolean)
      .map((pair) => {
        const [from, to] = pair.split("->").map((s) => s.trim());
        return { from, to };
      });

    try {
      await createWorkflowDefinition({ name, states, transitions });
      setName("");
      await refresh();
    } catch {
      setError("Failed to create workflow definition (check name uniqueness and permissions)");
    }
  }

  async function handleDeleteDefinition(id: string) {
    try {
      await deleteWorkflowDefinition(id);
      await refresh();
    } catch {
      setError("Failed to delete workflow definition");
    }
  }

  async function handleStartInstance(definitionId: string) {
    try {
      await createWorkflowInstance(definitionId);
      await refresh();
    } catch {
      setError("Failed to start workflow instance");
    }
  }

  async function handleTransition(instanceId: string, toState: string) {
    try {
      await transitionWorkflowInstance(instanceId, toState);
      await refresh();
    } catch {
      setError("Transition not allowed from the current state");
    }
  }

  return (
    <div className="min-h-screen bg-slate-50">
      <NavBar />
      <main className="mx-auto max-w-5xl space-y-8 p-6">
        <h1 className="text-2xl font-semibold text-slate-900">Workflows</h1>
        {error && <p className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}

        <section className="rounded-xl bg-white p-6 shadow-sm">
          <h2 className="mb-4 text-sm font-semibold uppercase tracking-wide text-slate-500">
            New workflow definition
          </h2>
          <form onSubmit={handleCreateDefinition} className="grid gap-3 sm:grid-cols-2">
            <label className="block sm:col-span-2">
              <span className="mb-1 block text-sm font-medium text-slate-700">Name</span>
              <input required className="input" value={name} onChange={(e) => setName(e.target.value)} />
            </label>
            <label className="block">
              <span className="mb-1 block text-sm font-medium text-slate-700">States (comma-separated)</span>
              <input
                required
                className="input"
                value={statesInput}
                onChange={(e) => setStatesInput(e.target.value)}
              />
            </label>
            <label className="block">
              <span className="mb-1 block text-sm font-medium text-slate-700">
                Transitions (from-&gt;to, comma-separated)
              </span>
              <input
                className="input"
                value={transitionsInput}
                onChange={(e) => setTransitionsInput(e.target.value)}
              />
            </label>
            <button
              type="submit"
              className="w-fit rounded-md bg-forge-600 px-4 py-2 text-sm font-semibold text-white hover:bg-forge-700 sm:col-span-2"
            >
              Create definition
            </button>
          </form>
        </section>

        <section className="rounded-xl bg-white p-6 shadow-sm">
          <h2 className="mb-4 text-sm font-semibold uppercase tracking-wide text-slate-500">
            Definitions {loading && "(loading...)"}
          </h2>
          <div className="space-y-3">
            {definitions.map((def) => (
              <div key={def.id} className="flex items-center justify-between rounded-lg border border-slate-200 p-4">
                <div>
                  <p className="font-medium text-slate-900">
                    {def.name} <span className="text-xs text-slate-400">v{def.version}</span>
                  </p>
                  <p className="text-xs text-slate-500">States: {def.states.join(" -> ")}</p>
                </div>
                <div className="flex gap-2">
                  <button
                    onClick={() => handleStartInstance(def.id)}
                    className="rounded-md border border-forge-600 px-3 py-1.5 text-xs font-medium text-forge-700 hover:bg-forge-50"
                  >
                    Start instance
                  </button>
                  <button
                    onClick={() => handleDeleteDefinition(def.id)}
                    className="rounded-md border border-red-300 px-3 py-1.5 text-xs font-medium text-red-600 hover:bg-red-50"
                  >
                    Delete
                  </button>
                </div>
              </div>
            ))}
            {definitions.length === 0 && !loading && (
              <p className="text-sm text-slate-500">No workflow definitions yet.</p>
            )}
          </div>
        </section>

        <section className="rounded-xl bg-white p-6 shadow-sm">
          <h2 className="mb-4 text-sm font-semibold uppercase tracking-wide text-slate-500">Instances</h2>
          <div className="space-y-3">
            {instances.map((instance) => {
              const definition = definitions.find((d) => d.id === instance.workflowDefinitionId);
              const nextStates =
                definition?.transitions
                  .filter((t) => t.from === instance.currentState)
                  .map((t) => t.to) ?? [];
              return (
                <div key={instance.id} className="rounded-lg border border-slate-200 p-4">
                  <div className="flex items-center justify-between">
                    <div>
                      <p className="font-medium text-slate-900">{definition?.name ?? "Unknown workflow"}</p>
                      <p className="text-xs text-slate-500">
                        Current state: <span className="font-semibold">{instance.currentState}</span>
                      </p>
                    </div>
                    <div className="flex gap-2">
                      {nextStates.map((toState) => (
                        <button
                          key={toState}
                          onClick={() => handleTransition(instance.id, toState)}
                          className="rounded-md bg-forge-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-forge-700"
                        >
                          -&gt; {toState}
                        </button>
                      ))}
                    </div>
                  </div>
                  {instance.history.length > 0 && (
                    <ol className="mt-3 space-y-1 text-xs text-slate-400">
                      {instance.history.map((entry, idx) => (
                        <li key={idx}>
                          {entry.from} -&gt; {entry.to} at {new Date(entry.at).toLocaleString()}
                        </li>
                      ))}
                    </ol>
                  )}
                </div>
              );
            })}
            {instances.length === 0 && !loading && (
              <p className="text-sm text-slate-500">No workflow instances yet.</p>
            )}
          </div>
        </section>
      </main>
    </div>
  );
}

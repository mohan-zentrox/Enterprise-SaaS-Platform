import { AxiosError } from "axios";
import type { ApiErrorBody } from "@/types";

/**
 * Turns an axios failure into something worth showing a person.
 *
 * The backend already returns a human-readable `message` in its ErrorResponse envelope (see
 * docs/API.md), and for 403/409 that message is the whole point — "this is the last active owner",
 * "system role cannot be modified", "you cannot grant permissions you do not hold yourself". The
 * pages previously swallowed all of it and showed a fixed string, which turned a precise
 * server-side explanation into a shrug.
 */
export function describeApiError(error: unknown, fallback: string): string {
  if (error instanceof AxiosError) {
    const body = error.response?.data as ApiErrorBody | undefined;
    if (body?.details?.length) {
      return `${body.message ?? fallback} (${body.details.join("; ")})`;
    }
    if (body?.message) {
      return body.message;
    }
    if (!error.response) {
      return "Could not reach the server. Check that the backend is running.";
    }
  }
  return fallback;
}

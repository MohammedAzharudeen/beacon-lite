import type { ReactNode } from "react";
import type { BeaconApiError } from "../../api/beaconApi";

export function EmptyState({
  title,
  children,
}: {
  title: string;
  children?: ReactNode;
}) {
  return (
    <div className="state" role="status">
      <b>{title}</b>
      {children}
    </div>
  );
}

export function ErrorState({
  error,
  onRetry,
}: {
  error: BeaconApiError;
  onRetry?: () => void;
}) {
  return (
    <div className="state error" role="alert">
      <b>{error.message}</b>
      {error.hint && <div>{error.hint}</div>}
      {onRetry && (
        <button
          type="button"
          className="btn ghost small"
          style={{ marginTop: 10 }}
          onClick={onRetry}
        >
          Try again
        </button>
      )}
    </div>
  );
}

export function Skeleton({ height = 80 }: { height?: number }) {
  return (
    <div
      className="skeleton"
      style={{ height }}
      aria-label="Loading"
      role="progressbar"
    />
  );
}

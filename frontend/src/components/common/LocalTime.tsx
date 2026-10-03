interface Props {
  iso: string | null;
  withDate?: boolean;
}

/** Shows a UTC timestamp in the viewer's local time zone. */
export function LocalTime({ iso, withDate = true }: Props) {
  if (!iso) return <span>—</span>;
  const date = new Date(iso);
  const text = withDate
    ? date.toLocaleString(undefined, {
        day: "numeric",
        month: "short",
        hour: "2-digit",
        minute: "2-digit",
      })
    : date.toLocaleTimeString(undefined, {
        hour: "2-digit",
        minute: "2-digit",
      });
  return (
    <time dateTime={iso} title={date.toISOString()}>
      {text}
    </time>
  );
}

/** A snapshot window, e.g. "3 Oct 08:00 → 14:00"; changes are never shown with an exact time. */
export function TimeWindow({ start, end }: { start: string; end: string }) {
  return (
    <span>
      <LocalTime iso={start} /> →{" "}
      <LocalTime
        iso={end}
        withDate={
          new Date(start).toDateString() !== new Date(end).toDateString()
        }
      />
    </span>
  );
}

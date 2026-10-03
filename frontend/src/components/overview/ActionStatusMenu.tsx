import { useState } from "react";
import type { ActionStatus } from "../../types/beacon";

const OPTIONS: { value: ActionStatus; label: string }[] = [
  { value: "TODO", label: "To do" },
  { value: "DONE", label: "Done" },
  { value: "DISMISSED", label: "Dismiss" },
];

/** To do / Done / Dismiss for one action; dismissed actions leave the top 5. */
export function ActionStatusMenu({
  status,
  onChange,
}: {
  status: ActionStatus;
  onChange: (s: ActionStatus) => void;
}) {
  const [open, setOpen] = useState(false);
  const current = OPTIONS.find((o) => o.value === status)?.label ?? "To do";
  return (
    <div className="menu">
      <button
        type="button"
        className="btn ghost small"
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen((o) => !o)}
      >
        {current} ▾
      </button>
      {open && (
        <div className="menu-list" role="menu">
          {OPTIONS.map((o) => (
            <button
              key={o.value}
              type="button"
              role="menuitemradio"
              aria-checked={o.value === status}
              onClick={() => {
                setOpen(false);
                onChange(o.value);
              }}
            >
              {o.label}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

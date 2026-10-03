import { useId, useState } from "react";

interface Props {
  text: string;
  label?: string;
}

/** An "ⓘ" button that explains a number: what it means, how it's calculated, data date. */
export function Tooltip({ text, label = "More information" }: Props) {
  const [open, setOpen] = useState(false);
  const id = useId();
  return (
    <span
      className="tip"
      onMouseEnter={() => setOpen(true)}
      onMouseLeave={() => setOpen(false)}
    >
      <button
        type="button"
        className="tip-btn"
        aria-label={label}
        aria-describedby={open ? id : undefined}
        aria-expanded={open}
        onClick={() => setOpen((o) => !o)}
        onBlur={() => setOpen(false)}
        onFocus={() => setOpen(true)}
      >
        i
      </button>
      {open && (
        <span role="tooltip" id={id} className="tip-box">
          {text}
        </span>
      )}
    </span>
  );
}

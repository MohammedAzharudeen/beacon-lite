import { useCallback, useEffect, useId, useRef, useState } from "react";
import { createPortal } from "react-dom";

interface Props {
  text: string;
  label?: string;
}

const WIDTH = 260;
const GAP = 6;
const EDGE = 8;

/**
 * An "ⓘ" button that explains a number: what it means, how it's calculated, data date.
 * The explanation is rendered at the end of the page (a portal) with fixed positioning, so no card
 * can clip it, cover it or change its text style; it opens below the button, or above when there
 * is no room, and stays inside the window.
 */
export function Tooltip({ text, label = "More information" }: Props) {
  const [open, setOpen] = useState(false);
  const [pos, setPos] = useState<{ left: number; top: number } | null>(null);
  const id = useId();
  const btnRef = useRef<HTMLButtonElement>(null);
  const boxRef = useRef<HTMLSpanElement>(null);

  const place = useCallback(() => {
    const btn = btnRef.current;
    if (!btn) return;
    const r = btn.getBoundingClientRect();
    const height = boxRef.current?.offsetHeight ?? 0;
    const left = Math.min(
      Math.max(r.right - WIDTH, EDGE),
      window.innerWidth - WIDTH - EDGE,
    );
    const below = r.bottom + GAP;
    const top =
      below + height > window.innerHeight - EDGE && r.top - GAP - height > EDGE
        ? r.top - GAP - height
        : below;
    setPos({ left, top });
  }, []);

  useEffect(() => {
    if (!open) return;
    place();
    // Second pass once the box has its real height (decides above / below)
    const frame = requestAnimationFrame(place);
    const close = () => setOpen(false);
    window.addEventListener("scroll", close, true);
    window.addEventListener("resize", close);
    return () => {
      cancelAnimationFrame(frame);
      window.removeEventListener("scroll", close, true);
      window.removeEventListener("resize", close);
    };
  }, [open, place]);

  return (
    <span
      className="tip"
      onMouseEnter={() => setOpen(true)}
      onMouseLeave={() => setOpen(false)}
    >
      <button
        ref={btnRef}
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
      {open &&
        createPortal(
          <span
            ref={boxRef}
            role="tooltip"
            id={id}
            className="tip-box"
            style={{
              width: WIDTH,
              left: pos?.left ?? -9999,
              top: pos?.top ?? -9999,
            }}
          >
            {text}
          </span>,
          document.body,
        )}
    </span>
  );
}

import { useEffect, useRef, useState } from "react";
import { beaconApi, BeaconApiError } from "../../api/beaconApi";
import { useLlmStatus } from "../../hooks/useBeacon";
import { Icon } from "../common/Icon";
import type { ChatResponse, ChatTurn } from "../../types/beacon";

const SUGGESTIONS = [
  "What should I restock first?",
  "Which sizes are missing in boots?",
  "What sold out since yesterday?",
  "Where am I losing shoppers?",
  "Compare me with the other stores",
];

interface Message {
  role: "user" | "assistant";
  content: string;
  meta?: ChatResponse;
}

const TOOL_LABEL: Record<string, string> = {
  get_store_overview: "store overview",
  get_restock_priorities: "restock priorities",
  get_size_gaps: "size gaps",
  get_promoted_sold_outs: "promoted sold-outs",
  get_journey_friction: "journey scorecard",
  get_recent_changes: "recent changes",
  get_pricing_insights: "pricing",
  search_products: "product search",
  get_catalog_health: "catalog health",
  compare_stores: "store comparison",
};

/** Ask Beacon: answers come from tools over verified data; store text is shown as plain text. */
export function ChatPanel({
  storeId,
  storeName,
}: {
  storeId: number;
  storeName: string;
}) {
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState("");
  const [busy, setBusy] = useState(false);
  const [elapsed, setElapsed] = useState(0);
  const [error, setError] = useState<BeaconApiError | null>(null);
  const llm = useLlmStatus();
  const listRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    setMessages([]);
    setError(null);
  }, [storeId]);

  useEffect(() => {
    if (!busy) return;
    setElapsed(0);
    const t = setInterval(() => setElapsed((s) => s + 1), 1000);
    return () => clearInterval(t);
  }, [busy]);

  // Scroll only the message list (never the page) to the newest message
  useEffect(() => {
    const list = listRef.current;
    if (list) list.scrollTop = list.scrollHeight;
  }, [messages, busy]);

  const ask = async (question: string) => {
    const text = question.trim();
    if (!text || busy) return;
    const history: ChatTurn[] = messages
      .slice(-10)
      .map((m) => ({ role: m.role, content: m.content }));
    setMessages((m) => [...m, { role: "user", content: text }]);
    setInput("");
    setBusy(true);
    setError(null);
    try {
      const response = await beaconApi.chat({
        storeId,
        message: text,
        history,
      });
      setMessages((m) => [
        ...m,
        { role: "assistant", content: response.answer, meta: response },
      ]);
    } catch (e) {
      setError(e instanceof BeaconApiError ? e : null);
    } finally {
      setBusy(false);
    }
  };

  return (
    <aside className="card chat" aria-label="Ask Beacon">
      <div className="hd">
        <h3>
          <span className="spark-ic" aria-hidden="true">
            <Icon name="sparkles" size={14} />
          </span>
          Ask Beacon
        </h3>
        <div className="sub">
          Questions about {storeName}, answered from verified numbers
        </div>
        <div className="status">
          <i className={llm.data?.reachable ? "" : "off"} />
          {llm.data
            ? llm.data.reachable
              ? `AI model: ${llm.data.model}`
              : "AI model offline: rule-based answers"
            : "Checking AI model…"}
        </div>
      </div>
      <div className="msgs" aria-live="polite" ref={listRef}>
        {messages.length === 0 && (
          <div className="sugg">
            {SUGGESTIONS.map((s) => (
              <button key={s} type="button" onClick={() => ask(s)}>
                {s}
              </button>
            ))}
          </div>
        )}
        {messages.map((m, i) => (
          <div key={i} className={`msg ${m.role === "user" ? "user" : "bot"}`}>
            {m.content}
            {m.meta && <Evidence meta={m.meta} />}
          </div>
        ))}
        {busy && (
          <div className="msg bot" role="status">
            Thinking… {elapsed}s
          </div>
        )}
        {error && (
          <div className="msg bot" role="alert">
            <b>{error.message}</b> {error.hint}
          </div>
        )}
      </div>
      <form
        className="compose"
        onSubmit={(e) => {
          e.preventDefault();
          ask(input);
        }}
      >
        <label className="sr-only" htmlFor="chat-input">
          Ask a question
        </label>
        <input
          id="chat-input"
          className="input"
          maxLength={1000}
          placeholder="Ask about restock, sizes, changes…"
          value={input}
          onChange={(e) => setInput(e.target.value)}
        />
        <button type="submit" className="btn" disabled={busy || !input.trim()}>
          Ask
        </button>
      </form>
    </aside>
  );
}

function Evidence({ meta }: { meta: ChatResponse }) {
  if (meta.provider === "SCOPE") {
    return (
      <div className="evid">
        <span className="pill mute">Out of scope for public data</span>
      </div>
    );
  }
  return (
    <div className="evid">
      {meta.validatorFallback ? (
        <span className="pill warn">
          Safe answer: AI draft failed the number check
        </span>
      ) : (
        <span className="pill good">
          ✓ {meta.numbersVerified} numbers verified
        </span>
      )}
      {meta.toolsUsed.map((t) => (
        <span key={t} className="pill mute">
          Used: {TOOL_LABEL[t] ?? t}
        </span>
      ))}
      {meta.confidence === "ESTIMATE" && (
        <span className="pill warn">Includes estimates</span>
      )}
      <span className="pill mute">
        {meta.provider === "LLM" ? "AI model" : "Rule-based"}
      </span>
    </div>
  );
}

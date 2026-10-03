import {
  createContext,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import type { Assumptions, DemandSignal, RestockRow } from "../../types/beacon";

export interface DemandOverrides {
  weeklyDemandBaselineUnits: number;
  signalWeights: Assumptions["demand"]["signalWeights"];
}

interface Value {
  base: Assumptions | null;
  demand: DemandOverrides | null;
  edited: boolean;
  setDemand: (d: DemandOverrides) => void;
  reset: () => void;
}

const Ctx = createContext<Value>({
  base: null,
  demand: null,
  edited: false,
  setDemand: () => {},
  reset: () => {},
});

const WEIGHT_KEY: Record<
  DemandSignal,
  keyof Assumptions["demand"]["signalWeights"]
> = {
  BEST_SELLER_COLLECTION: "bestSellerCollection",
  PROMOTED: "promoted",
  SELL_OUT_SPEED: "sellOutSpeed",
  FULL_PRICE: "fullPrice",
  RECENTLY_LAUNCHED: "recentlyLaunched",
};

/**
 * Holds the server's assumptions plus any values the merchant is trying out in the Assumptions
 * panel, so restock rows re-rank instantly without a rescan. Edits are not saved to the server.
 */
export function AssumptionsProvider({
  base,
  children,
}: {
  base: Assumptions | null;
  children: ReactNode;
}) {
  const [override, setOverride] = useState<DemandOverrides | null>(null);
  const value = useMemo<Value>(() => {
    const demand =
      override ??
      (base
        ? {
            weeklyDemandBaselineUnits: base.demand.weeklyDemandBaselineUnits,
            signalWeights: base.demand.signalWeights,
          }
        : null);
    return {
      base,
      demand,
      edited: override !== null,
      setDemand: setOverride,
      reset: () => setOverride(null),
    };
  }, [base, override]);
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export const useAssumptionValues = () => useContext(Ctx);

/** $ at risk with the current (possibly edited) demand values: price × demand × baseline × gap. */
export function estimateAtRisk(
  row: RestockRow,
  demand: DemandOverrides | null,
): number {
  if (!demand) return Number(row.atRiskPerWeek.amount);
  const score = Math.min(
    1,
    row.signals.reduce(
      (sum, s) => sum + demand.signalWeights[WEIGHT_KEY[s]],
      0,
    ),
  );
  return (
    Number(row.price.amount) *
    score *
    demand.weeklyDemandBaselineUnits *
    row.gapScore
  );
}

import { formatAmount } from "./format";

interface Props {
  amount: string | number;
  currency: string | null;
  decimals?: number;
}

/** Money in the store's own currency; never assumes dollars. */
export function Money({ amount, currency, decimals = 0 }: Props) {
  const value = formatAmount(amount, decimals);
  if (!currency) {
    return (
      <span>
        {value} <span className="pill mute">Currency unknown</span>
      </span>
    );
  }
  return (
    <span>
      {value} {currency}
    </span>
  );
}

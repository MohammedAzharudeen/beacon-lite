/** Shared number formatting so every screen shows numbers the same way. */
export function formatAmount(amount: string | number, decimals = 0): string {
  const value = typeof amount === "string" ? Number(amount) : amount;
  return value.toLocaleString("en-US", {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  });
}

export function formatCount(value: number): string {
  return value.toLocaleString("en-US");
}

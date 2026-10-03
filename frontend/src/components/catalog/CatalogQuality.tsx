import type { InsightReport } from "../../types/beacon";
import { Money } from "../common/Money";
import { Tooltip } from "../common/Tooltip";

export function CatalogQuality({ report }: { report: InsightReport }) {
  const q = report.catalogQuality;
  const p = report.pricing;
  const total = report.catalog.products;
  return (
    <div className="grid2">
      <section className="card" aria-label="Catalog quality">
        <div className="hd">
          <h3>Catalog quality</h3>
        </div>
        <div className="body stat-list">
          <div>
            <span>Products with fewer than {q.minImages} images</span>
            <b>
              {q.fewImages} of {total.toLocaleString("en-US")}
            </b>
          </div>
          <div>
            <span>
              Image alt text (sampled){" "}
              <Tooltip text="The catalog feed has no alt-text field, so alt text is checked on a sample of product pages' data. This never claims all products lack alt text." />
            </span>
            <b>{q.altText.text}</b>
          </div>
          <div>
            <span>Short descriptions</span>
            <b>{q.thinDescriptions.toLocaleString("en-US")}</b>
          </div>
          <div>
            <span>Short titles</span>
            <b>{q.thinTitles.toLocaleString("en-US")}</b>
          </div>
          <div>
            <span>No product type</span>
            <b>{q.blankProductType}</b>
          </div>
          <div>
            <span>Untagged products</span>
            <b>{q.untagged}</b>
          </div>
          <div>
            <span>Types differing only by letter case</span>
            <b>
              {q.caseVariantTypes.length === 0
                ? "None"
                : q.caseVariantTypes.map((g) => g.join(" / ")).join("; ")}
            </b>
          </div>
          <div>
            <span>Spreadsheet errors in types or titles</span>
            <b>
              {q.spreadsheetErrors.length === 0
                ? "None"
                : q.spreadsheetErrors.join(", ")}
            </b>
          </div>
          {q.fewImagesExamples.length > 0 && (
            <details>
              <summary>Products with few images</summary>
              <ul style={{ margin: "8px 0 0 18px" }}>
                {q.fewImagesExamples.map((e) => (
                  <li key={e.productId}>
                    <a href={e.productUrl} target="_blank" rel="noreferrer">
                      {e.title}
                    </a>
                  </li>
                ))}
              </ul>
            </details>
          )}
        </div>
      </section>
      <section className="card" aria-label="Pricing">
        <div className="hd">
          <h3>Pricing</h3>
        </div>
        <div className="body stat-list">
          <div>
            <span>
              Compare-at price equals selling price{" "}
              <Tooltip text="Catalog clean-up: a compare-at price identical to the price. Whether a strike-through shows depends on the theme, so no claim is made about what shoppers see." />
            </span>
            <b>{p.compareAtEqualsPrice.toLocaleString("en-US")} products</b>
          </div>
          <div>
            <span>Discounted products</span>
            <b>{p.discounted.toLocaleString("en-US")}</b>
          </div>
          {Object.entries(p.discountBuckets).map(([bucket, count]) => (
            <div key={bucket}>
              <span>· discounted {bucket}</span>
              <b>{count.toLocaleString("en-US")}</b>
            </div>
          ))}
          <div>
            <span>Deep discounts ({p.deepDiscountPercent}% or more)</span>
            <b>{p.deepDiscount.toLocaleString("en-US")}</b>
          </div>
          <div>
            <span>Dead stock (in stock, old, discounted)</span>
            <b>{p.deadStock.toLocaleString("en-US")}</b>
          </div>
          <div>
            <span>Free-shipping threshold</span>
            <b>
              {p.freeShipping.threshold !== null ? (
                <>
                  <Money
                    amount={p.freeShipping.threshold}
                    currency={report.currency}
                    decimals={2}
                  />{" "}
                  vs median price{" "}
                  {p.freeShipping.medianPrice !== null && (
                    <Money
                      amount={p.freeShipping.medianPrice}
                      currency={report.currency}
                      decimals={2}
                    />
                  )}
                </>
              ) : p.freeShipping.text ? (
                "Mentioned without an amount"
              ) : (
                "Not found"
              )}
            </b>
          </div>
          {p.freeShipping.text && (
            <div className="sub">Store text: "{p.freeShipping.text}"</div>
          )}
        </div>
      </section>
    </div>
  );
}

#!/usr/bin/env python3
"""Records the public storefront responses Beacon Lite reads, for offline tests and demo data.

Run on a machine with internet access (the build sandbox has none):

    python3 scripts/record-stores.py                    # full recording of the three demo stores
    python3 scripts/record-stores.py --catalog-only     # catalog pages only (for repeated snapshots)
    python3 scripts/record-stores.py www.example.com    # any other store

Rules it follows (same as the app):
  * robots.txt is read first; every path is checked against it and skipped when disallowed
  * one request at a time, with a pause between requests, and a clear User-Agent
  * public pages only: no login, cart, checkout or sort-order pages

Output: .bootstrap/recordings/<UTC time>/<host>/ with gzip files and manifest.json.
Only the Python standard library and curl are used.
"""

import argparse
import datetime
import gzip
import json
import os
import re
import subprocess
import sys
import time
from urllib.parse import quote

DEFAULT_STORES = ["www.stevemadden.com", "www.reebok.com", "petalandpup.com"]
USER_AGENT = "BeaconLite/1.0 (+contact in README)"
PRODUCT_TOKEN = "beaconlite"
DELAY_SECONDS = 1.0
MAX_CATALOG_PAGES = 100  # Shopify's public feed stops after page 100
BEST_SELLER_PATTERN = re.compile(r"best-seller|bestseller|best-of|top-sell", re.I)
POLICY_LINK_PATTERN = re.compile(r"return|refund|exchange|shipping|delivery", re.I)
EXCLUDED_TAG_PATTERN = re.compile(r"pre-?order|coming[ -]soon|purple-dot|back-?order", re.I)
EXCLUDED_TYPE_PATTERN = re.compile(r"gift[ -]?cards?|gwp|membership|sgdonation|trashie|bundles?", re.I)
FEATURED_COLLECTIONS = 30
FEATURED_COLLECTION_PAGES = 4
FOOTER_PAGES = 6
SOLD_OUT_PAGES = 5
ALT_TEXT_PRODUCTS = 30
SEARCH_TERMS = 5


class Robots:
    """robots.txt rules per RFC 9309: merged '*' groups, wildcards, longest match, Allow wins ties."""

    def __init__(self, text):
        own, star, agents, last_agent = [], [], [], False
        for raw in (text or "").splitlines():
            line = raw.split("#", 1)[0].strip()
            if ":" not in line:
                continue
            key, value = (p.strip() for p in line.split(":", 1))
            key = key.lower()
            if key == "user-agent":
                if not last_agent:
                    agents = []
                agents.append(value.lower())
                last_agent = True
                continue
            last_agent = False
            if key not in ("allow", "disallow") or not value:
                continue
            rule = (key == "allow", value, self._compile(value))
            if PRODUCT_TOKEN in agents:
                own.append(rule)
            if "*" in agents:
                star.append(rule)
        self.rules = own or star

    @staticmethod
    def _compile(value):
        anchored = value.endswith("$")
        body = value[:-1] if anchored else value
        pattern = ".*".join(re.escape(part) for part in body.split("*"))
        return re.compile(pattern + ("$" if anchored else ""))

    def allowed(self, path):
        if path == "/robots.txt":
            return True, None
        best = None
        for allow, value, regex in self.rules:
            if regex.match(path):
                if best is None or len(value) > len(best[1]) or (len(value) == len(best[1]) and allow):
                    best = (allow, value)
        return (True, None) if best is None else (best[0], best[1])


class Recorder:
    def __init__(self, host, out_dir):
        self.host = host
        self.out_dir = out_dir
        self.manifest = []
        self.robots = None
        self.last_request = 0.0
        self.last_redirect = None
        os.makedirs(out_dir, exist_ok=True)

    def get(self, path, name):
        """Fetches a path if robots.txt allows it; saves the body gzip-compressed; returns text or None."""
        if self.robots is not None:
            ok, rule = self.robots.allowed(path)
            if not ok:
                self.manifest.append({"path": path, "robots": "DISALLOWED", "rule": rule})
                print(f"  skip  {path}  (robots: Disallow {rule})")
                return None
        wait = DELAY_SECONDS - (time.time() - self.last_request)
        if wait > 0:
            time.sleep(wait)
        url = f"https://{self.host}{path}"
        started = time.time()
        result = subprocess.run(
            ["curl", "-sS", "--compressed", "-A", USER_AGENT, "--max-time", "60",
             "-o", "-", "-w", "\n%{http_code} %{content_type} %{redirect_url}", url],
            capture_output=True,
        )
        self.last_request = time.time()
        if result.returncode != 0:
            print(f"  fail  {path}  ({result.stderr.decode(errors='replace').strip()})")
            self.manifest.append({"path": path, "robots": "ALLOWED", "error": "network"})
            return None
        body, _, trailer = result.stdout.rpartition(b"\n")
        status, _, rest = trailer.decode().partition(" ")
        content_type, _, redirect = rest.partition(" ")
        self.last_redirect = redirect.strip() or None
        file_name = f"{name}.gz"
        with gzip.open(os.path.join(self.out_dir, file_name), "wb") as f:
            f.write(body)
        ms = int((time.time() - started) * 1000)
        self.manifest.append({"path": path, "robots": "ALLOWED", "status": int(status),
                              "contentType": content_type, "bytes": len(body), "ms": ms,
                              "file": file_name})
        print(f"  {status}   {path}  {len(body):,} bytes")
        return body.decode("utf-8", errors="replace") if status.startswith("2") else None

    def save_manifest(self, extra):
        with open(os.path.join(self.out_dir, "manifest.json"), "w") as f:
            json.dump({"host": self.host, "userAgent": USER_AGENT, **extra,
                       "requests": self.manifest}, f, indent=1)


def paged_products(rec, base_path, prefix, max_pages):
    products = []
    for page in range(1, max_pages + 1):
        text = rec.get(f"{base_path}?limit=250&page={page}", f"{prefix}-page-{page:03d}")
        items = json.loads(text).get("products", []) if text else []
        if not items:
            break
        products.extend(items)
    return products


def record_store(host, root, catalog_only):
    print(f"\n== {host}")
    rec = Recorder(host, os.path.join(root, host))
    robots_text = rec.get("/robots.txt", "robots.txt")
    status = rec.manifest[-1].get("status", 0)
    if 300 <= status < 400:
        print(f"  robots.txt redirects to {rec.last_redirect}; re-run with that store address")
        rec.save_manifest({"redirect": rec.last_redirect})
        return
    if robots_text is None and not 400 <= status < 500:
        print("  robots.txt unavailable: nothing else is fetched (same rule as the app)")
        rec.save_manifest({})
        return
    rec.robots = Robots(robots_text or "")  # 4xx: no robots.txt, everything allowed
    catalog = paged_products(rec, "/products.json", "products", MAX_CATALOG_PAGES)
    extra = {"products": len(catalog)}
    if catalog_only:
        rec.save_manifest(extra)
        return

    collections = []
    for page in range(1, 11):
        text = rec.get(f"/collections.json?limit=250&page={page}", f"collections-page-{page:03d}")
        items = json.loads(text).get("collections", []) if text else []
        if not items:
            break
        collections.extend(items)
    best_sellers = [c["handle"] for c in collections if BEST_SELLER_PATTERN.search(c.get("handle", ""))]
    for handle in best_sellers:
        paged_products(rec, f"/collections/{handle}/products.json", f"collection-{handle}", 20)

    home = rec.get("/", "home.html") or ""
    featured = []
    for handle in re.findall(r'href="(?:https://[^"/]+)?/collections/([a-z0-9][a-z0-9\-]*)"', home):
        if handle not in featured and handle not in best_sellers and handle != "all":
            featured.append(handle)
    for handle in featured[:FEATURED_COLLECTIONS]:
        paged_products(rec, f"/collections/{handle}/products.json", f"collection-{handle}",
                       FEATURED_COLLECTION_PAGES)

    for path, name in (("/policies/refund-policy", "policy-refund"),
                       ("/policies/shipping-policy", "policy-shipping")):
        rec.get(path, name + ".html")
    footer = []
    for handle in re.findall(r'href="(?:https://[^"/]+)?/pages/([a-z0-9][a-z0-9\-]*)"', home):
        if POLICY_LINK_PATTERN.search(handle) and handle not in footer:
            footer.append(handle)
    for handle in footer[:FOOTER_PAGES]:
        rec.get(f"/pages/{handle}", f"page-{handle}.html")

    types = {}
    for p in catalog:
        t = (p.get("product_type") or "").strip().lower()
        if t:
            types[t] = types.get(t, 0) + 1
    terms = sorted(types, key=lambda t: (-types[t], t))[:SEARCH_TERMS]
    for i, term in enumerate(terms, 1):
        rec.get(f"/search/suggest.json?q={quote(term)}&resources%5Btype%5D=product",
                f"search-{i}")

    def excluded(p):
        tags = p.get("tags") or []
        tags = tags if isinstance(tags, list) else str(tags).split(",")
        return any(EXCLUDED_TAG_PATTERN.search(t) for t in tags) or \
            EXCLUDED_TYPE_PATTERN.search(p.get("product_type") or "")

    sold_out = [p for p in catalog
                if p.get("variants") and not any(v.get("available") for v in p["variants"])
                and not excluded(p)]
    for p in sold_out[:SOLD_OUT_PAGES]:
        rec.get(f"/products/{p['handle']}", f"product-page-{p['handle']}.html")
    for p in catalog[:ALT_TEXT_PRODUCTS]:
        rec.get(f"/products/{p['handle']}.json", f"product-json-{p['handle']}")

    extra.update({"bestSellerCollections": best_sellers, "featuredCollections": featured[:FEATURED_COLLECTIONS],
                  "footerPages": footer[:FOOTER_PAGES], "searchTerms": terms})
    rec.save_manifest(extra)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("stores", nargs="*", default=DEFAULT_STORES)
    parser.add_argument("--catalog-only", action="store_true", help="record robots.txt and catalog pages only")
    parser.add_argument("--out", default=os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                                      "..", ".bootstrap", "recordings"))
    args = parser.parse_args()
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H-%M-%SZ")
    root = os.path.normpath(os.path.join(args.out, stamp))
    for host in args.stores:
        try:
            record_store(host.lower(), root, args.catalog_only)
        except Exception as e:  # one store failing must not stop the others
            print(f"  error recording {host}: {e}", file=sys.stderr)
    print(f"\nSaved to {root}")


if __name__ == "__main__":
    main()

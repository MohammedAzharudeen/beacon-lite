# Test fixtures

Real responses recorded from the public storefronts on 3 Oct 2026 with `scripts/record-stores.py`
(robots.txt obeyed, one request at a time, User-Agent `BeaconLite/1.0`). Nothing here is invented.

| Folder | What it is |
|---|---|
| `<store>/robots.txt` | The store's robots.txt as recorded on 3 Oct 2026 |
| `<store>/recording/` | A subset of one full recording, kept small for adapter tests: robots.txt, catalog page 1, the collection index, one best-seller collection, the home page, policy or footer pages, one search response (where allowed), one sold-out product page and a few product JSON files. `manifest.json` lists each request with its real status and content type. |
| `meshki/recording/` | Meshki's bot-protection (Cloudflare challenge) responses, used to test the "store blocks automated tools" message |

Two entries are re-mapped so the subset behaves like a complete store, and are marked with a `note`
in `manifest.json`:

* catalog page 2 is answered with the store's real empty last page (the catalog in the subset stops
  after page 1);
* the detection request `/products.json?limit=1` is answered with the recorded catalog page 1
  (Meshki: its recorded challenge page).

The full recordings are converted to the demo snapshots in `src/main/resources/snapshots/` by
`RecordingConverter` (see its Javadoc).

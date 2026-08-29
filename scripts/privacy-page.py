#!/usr/bin/env python3
"""Builds the public privacy page from the app's own privacy strings.

The page and the in-app Privacy screen must say the same thing: the listing links to the
page, the app shows the screen, and a discrepancy between them is a broken promise rather
than a formatting slip. Generating one from the other makes drift impossible -- there is a
single source of text, and it is the one the app itself renders.

Reads values/strings.xml (and values-<lang>/strings.xml per translation) and writes one
self-contained HTML file per language into the output directory.

    ./scripts/privacy-page.py build/privacy
"""
import html
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res"

# Order matters: it is the reading order of the page, and mirrors the in-app screen.
SECTIONS = [
    ("privacy_no_network_title", "privacy_no_network_body"),
    ("privacy_read_only_title", "privacy_read_only_body"),
    ("privacy_storage_title", "privacy_storage_body"),
    ("privacy_sharing_title", "privacy_sharing_body"),
    ("privacy_control_title", "privacy_control_body"),
    ("privacy_purchases_title", "privacy_purchases_body"),
    ("privacy_contact_title", "privacy_contact_body"),
]


def strings_for(lang_dir: Path) -> dict[str, str]:
    path = lang_dir / "strings.xml"
    if not path.exists():
        return {}
    root = ET.parse(path).getroot()
    out = {}
    for node in root.findall("string"):
        name = node.get("name")
        text = "".join(node.itertext())
        # Android escapes apostrophes for its own parser; the web wants them plain.
        out[name] = text.replace("\\'", "'").replace('\\"', '"')
    return out


def render(values: dict[str, str], lang: str, updated: str) -> str:
    def get(key: str) -> str:
        return values.get(key, "")

    body = []
    for title_key, body_key in SECTIONS:
        title, text = get(title_key), get(body_key)
        if not title or not text:
            continue
        body.append(
            f"    <section>\n"
            f"      <h2>{html.escape(title)}</h2>\n"
            f"      <p>{html.escape(text)}</p>\n"
            f"    </section>"
        )

    app = get("app_name") or "Health Connect View"
    heading = get("privacy_title") or "Privacy"
    summary = get("privacy_summary")

    return f"""<!DOCTYPE html>
<html lang="{lang}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{html.escape(app)} — {html.escape(heading)}</title>
<style>
  :root {{
    color-scheme: light dark;
    --bg: #fbfcff; --fg: #1a1c1e; --muted: #43474e; --rule: #dfe2eb; --accent: #00696d;
  }}
  @media (prefers-color-scheme: dark) {{
    :root {{ --bg: #1a1c1e; --fg: #e2e2e6; --muted: #c3c7cf; --rule: #43474e; --accent: #4fd8dd; }}
  }}
  * {{ box-sizing: border-box; }}
  body {{
    margin: 0 auto; padding: 2.5rem 1.25rem 4rem; max-width: 42rem;
    background: var(--bg); color: var(--fg);
    font: 17px/1.65 -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
  }}
  h1 {{ font-size: 1.9rem; line-height: 1.2; margin: 0 0 .5rem; }}
  h2 {{ font-size: 1.15rem; margin: 2.25rem 0 .4rem; color: var(--accent); }}
  p {{ margin: 0 0 1rem; color: var(--muted); }}
  .lede {{ font-size: 1.1rem; color: var(--fg); margin-bottom: 2rem; }}
  footer {{ margin-top: 3rem; padding-top: 1.25rem; border-top: 1px solid var(--rule);
            font-size: .9rem; color: var(--muted); }}
  a {{ color: var(--accent); }}
</style>
</head>
<body>
  <h1>{html.escape(app)}</h1>
  <p class="lede">{html.escape(summary)}</p>
{chr(10).join(body)}
  <footer>
    <p>Last updated: {updated}</p>
  </footer>
</body>
</html>
"""


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    out_dir = Path(sys.argv[1])
    out_dir.mkdir(parents=True, exist_ok=True)

    updated = sys.argv[2] if len(sys.argv) > 2 else ""
    if not updated:
        import datetime
        updated = datetime.date.today().isoformat()

    default = strings_for(RES / "values")
    if not default:
        print("error: no default strings found", file=sys.stderr)
        return 1

    written = []
    # index.html is the default locale; each translation gets its own file.
    (out_dir / "index.html").write_text(render(default, "en", updated), encoding="utf-8")
    written.append(out_dir / "index.html")

    for lang_dir in sorted(RES.glob("values-*")):
        lang = lang_dir.name.removeprefix("values-")
        if lang in {"night"}:
            continue
        values = strings_for(lang_dir)
        if not values:
            continue
        # Fall back per key, matching Android: a partially translated locale shows English
        # for what it has not translated rather than an empty section.
        merged = {**default, **values}
        target = out_dir / f"index.{lang}.html"
        target.write_text(render(merged, lang, updated), encoding="utf-8")
        written.append(target)

    for path in written:
        print(path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

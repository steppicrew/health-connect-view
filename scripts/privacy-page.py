#!/usr/bin/env python3
"""Builds the public privacy page from the app's own privacy strings.

The page and the in-app Privacy screen must say the same thing: the listing links to the
page, the app shows the screen, and a discrepancy between them is a broken promise rather
than a formatting slip. Generating one from the other makes drift impossible -- there is a
single source of text, and it is the one the app itself renders.

Reads values/strings.xml (and values-<lang>/strings.xml per translation) and writes one
self-contained HTML file per language into the output directory.

    ./scripts/privacy-page.py build/privacy
    ./scripts/privacy-page.py --markdown PRIVACY.md

The second form writes the repository's PRIVACY.md from the same English strings, so the copy
on GitHub cannot fall behind either: it did once, a month of exports and routes missing. Its
date moves only when the text does, so regenerating an unchanged policy leaves the file alone.
"""
import html
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
# The contact section says the source is public so every claim can be checked; this is where.
SOURCE = "https://github.com/steppicrew/health-connect-view"
RES = ROOT / "app/src/main/res"

# Order matters: it is the reading order of the page, and mirrors the in-app screen.
SECTIONS = [
    ("privacy_data_title", "privacy_data_body"),
    ("privacy_no_network_title", "privacy_no_network_body"),
    ("privacy_read_only_title", "privacy_read_only_body"),
    ("privacy_storage_title", "privacy_storage_body"),
    ("privacy_sharing_title", "privacy_sharing_body"),
    ("privacy_routes_title", "privacy_routes_body"),
    ("privacy_control_title", "privacy_control_body"),
    ("privacy_purchases_title", "privacy_purchases_body"),
    ("privacy_contact_title", "privacy_contact_body"),
]


# Each translation under its own name, and the BCP 47 tag browsers send: Android's folder names
# say pt-rBR and zh-rCN, and keep Indonesian under its old code `in`.
LANGUAGES = {
    "en": ("en", "English"),
    "ar": ("ar", "العربية"),
    "de": ("de", "Deutsch"),
    "es": ("es", "Español"),
    "fr": ("fr", "Français"),
    "hi": ("hi", "हिन्दी"),
    "in": ("id", "Bahasa Indonesia"),
    "it": ("it", "Italiano"),
    "ja": ("ja", "日本語"),
    "ko": ("ko", "한국어"),
    "nl": ("nl", "Nederlands"),
    "pl": ("pl", "Polski"),
    "pt-rBR": ("pt-BR", "Português (Brasil)"),
    "ru": ("ru", "Русский"),
    "tr": ("tr", "Türkçe"),
    "zh-rCN": ("zh-CN", "简体中文"),
    "zh-rTW": ("zh-TW", "繁體中文"),
}
RTL = {"ar"}

# Offered, in its own words, to a reader whose browser prefers that language.
HINTS = {
    "en": "This page is also available in English.",
    "ar": "هذه الصفحة متاحة أيضًا باللغة العربية.",
    "de": "Diese Seite gibt es auch auf Deutsch.",
    "es": "Esta página también está disponible en español.",
    "fr": "Cette page est également disponible en français.",
    "hi": "यह पेज हिन्दी में भी उपलब्ध है।",
    "in": "Halaman ini juga tersedia dalam Bahasa Indonesia.",
    "it": "Questa pagina è disponibile anche in italiano.",
    "ja": "このページは日本語でもご覧いただけます。",
    "ko": "이 페이지는 한국어로도 보실 수 있습니다.",
    "nl": "Deze pagina is ook beschikbaar in het Nederlands.",
    "pl": "Ta strona jest dostępna również po polsku.",
    "pt-rBR": "Esta página também está disponível em português.",
    "ru": "Эта страница также доступна на русском языке.",
    "tr": "Bu sayfa Türkçe olarak da mevcuttur.",
    "zh-rCN": "本页面也提供简体中文版。",
    "zh-rTW": "本頁面也提供繁體中文版。",
}

# The script only *offers* the reader's language; it never navigates. Play rejected the policy
# URL as "indirect" (25.11.2026) while the root page switched to a translation by itself: the
# link must land on the policy text. A reader arriving from our own language list chose already.
OFFER = """<script>
(function () {
  if (document.referrer.indexOf(location.origin) === 0) return;
  var pages = %s, here = %s;
  var wanted = navigator.languages || [navigator.language || ""];
  for (var i = 0; i < wanted.length; i++) {
    var tag = wanted[i].toLowerCase();
    if (tag.indexOf("zh") === 0) tag = /hant|tw|hk|mo/.test(tag) ? "zh-tw" : "zh-cn";
    var base = tag.split("-")[0], key = tag in pages ? tag : base in pages ? base : undefined;
    // Another region of the same language before the next preference: pt-PT reads the Brazilian page.
    for (var k in pages) if (key === undefined && k.split("-")[0] === base) key = k;
    if (key === undefined) continue;
    if (key === here) return;
    var p = pages[key], a = document.createElement("a"), hint = document.getElementById("hint");
    a.href = p[0]; a.textContent = p[1]; a.lang = key; a.hreflang = key;
    a.dir = p[2] ? "rtl" : "ltr";
    hint.appendChild(a); hint.hidden = false;
    return;
  }
})();
</script>"""


def page_name(lang: str) -> str:
    return "index.html" if lang == "en" else f"index.{lang}.html"


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


def render(values: dict[str, str], lang: str, updated: str, languages: list[str]) -> str:
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

    tag = LANGUAGES[lang][0]
    # Every language under its own name. Choosing one is the reader's own click, never a redirect.
    options = "".join(
        f'<option value="{page_name(other)}" lang="{LANGUAGES[other][0]}"{" selected" if other == lang else ""}>'
        f'{html.escape(LANGUAGES[other][1])}</option>'
        for other in languages
    )
    offer = OFFER % (
        json.dumps({
            LANGUAGES[other][0].lower(): [page_name(other), HINTS[other], other in RTL] for other in languages
        }, ensure_ascii=False),
        json.dumps(tag.lower()),
    )

    return f"""<!DOCTYPE html>
<html lang="{tag}"{' dir="rtl"' if lang in RTL else ""}>
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
  nav {{ display: flex; justify-content: flex-end; margin: 0 0 1rem; }}
  select {{ font: inherit; font-size: .9rem; color: var(--fg); background: var(--bg);
            border: 1px solid var(--rule); border-radius: .4rem; padding: .25rem .5rem; }}
  .app {{ margin: 0 0 1.5rem; color: var(--fg); font-weight: 600; }}
  .hint {{ margin: 0 0 1.5rem; padding: .6rem .9rem; border: 1px solid var(--rule); border-radius: .5rem; }}
</style>
</head>
<body>
  <nav><select aria-label="Language" onchange="location.href = this.value">{options}</select></nav>
  <p class="hint" id="hint" hidden></p>
  <h1>{html.escape(heading)}</h1>
  <p class="app">{html.escape(app)}</p>
  <p class="lede">{html.escape(summary)}</p>
{chr(10).join(body)}
  <footer>
    <p>Last updated: {updated} · <a href="{SOURCE}">{SOURCE.removeprefix("https://")}</a></p>
  </footer>
{offer}
</body>
</html>
"""


MARKDOWN_NOTE = (
    "<!-- Generated by scripts/privacy-page.py from the app's privacy_* strings."
    " Edit those, not this file. -->"
)
UPDATED_LINE = re.compile(r"^Last updated: .*$", re.MULTILINE)


def render_markdown(values: dict[str, str], updated: str) -> str:
    app = values.get("app_name") or "Health Connect View"
    parts = [
        MARKDOWN_NOTE,
        f"# {values.get('privacy_title') or 'Privacy'}",
        f"**{app}**",
        f"Last updated: {updated}",
        values.get("privacy_summary", ""),
    ]
    for title_key, body_key in SECTIONS:
        title, text = values.get(title_key, ""), values.get(body_key, "")
        if title and text:
            parts += [f"## {title}", text]
    return "\n\n".join(p for p in parts if p) + "\n"


def write_markdown(target: Path, updated: str) -> int:
    default = strings_for(RES / "values")
    if not default:
        print("error: no default strings found", file=sys.stderr)
        return 1
    text = render_markdown(default, updated)
    if target.exists():
        old = target.read_text(encoding="utf-8")
        # Same policy, different day: keep the file as it is rather than restamp it.
        if UPDATED_LINE.sub("", old) == UPDATED_LINE.sub("", text):
            return 0
    target.write_text(text, encoding="utf-8")
    print(target)
    return 0


def main() -> int:
    if len(sys.argv) >= 3 and sys.argv[1] == "--markdown":
        import datetime
        return write_markdown(Path(sys.argv[2]), datetime.date.today().isoformat())
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

    # index.html is the default locale; each translation gets its own file.
    translations = {"en": default}
    for lang_dir in sorted(RES.glob("values-*")):
        lang = lang_dir.name.removeprefix("values-")
        values = strings_for(lang_dir)
        if lang not in LANGUAGES or not values:
            continue
        # Fall back per key, matching Android: a partially translated locale shows English
        # for what it has not translated rather than an empty section.
        translations[lang] = {**default, **values}
    missing = {d.name.removeprefix("values-") for d in RES.glob("values-*") if strings_for(d).get("privacy_title")} - LANGUAGES.keys()
    if missing:
        print(f"error: no name for {', '.join(sorted(missing))} in LANGUAGES", file=sys.stderr)
        return 1

    written = []
    for lang, values in translations.items():
        target = out_dir / page_name(lang)
        target.write_text(render(values, lang, updated, list(translations)), encoding="utf-8")
        written.append(target)

    for path in written:
        print(path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

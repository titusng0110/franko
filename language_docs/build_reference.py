from pathlib import Path
import re

root = Path(__file__).resolve().parent
summary = root / "SUMMARY.md"
output = root / "Franko_Language_Reference.md"

pattern = re.compile(r"\(([^)]+\.md)\)")
files = []
for line in summary.read_text(encoding="utf-8").splitlines():
    match = pattern.search(line)
    if match:
        name = match.group(1)
        if name not in files:
            files.append(name)

parts = []
for name in files:
    path = root / name
    if not path.exists():
        raise FileNotFoundError(path)
    parts.append(path.read_text(encoding="utf-8").rstrip())

output.write_text("\n\n---\n\n".join(parts) + "\n", encoding="utf-8")
print(f"Wrote {output}")

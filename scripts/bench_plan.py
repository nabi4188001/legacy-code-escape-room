#!/usr/bin/env python3
"""Turns the workflow's model choice into a build matrix.

Reads bench/models.json and the MODELS environment variable ("all" or a comma separated
list of catalogue ids) and writes `matrix=<json>` to the GitHub Actions output file.
"""
import json
import os
import sys

catalog = json.load(open("bench/models.json"))
wanted = os.environ.get("MODELS", "all").strip()
if wanted in ("", "all"):
    chosen = catalog
else:
    ids = [w.strip() for w in wanted.split(",") if w.strip()]
    known = {m["id"] for m in catalog}
    unknown = [i for i in ids if i not in known]
    if unknown:
        sys.exit("Unknown model ids: %s. Known: %s" % (", ".join(unknown), ", ".join(sorted(known))))
    chosen = [m for m in catalog if m["id"] in ids]

matrix = {"include": [{k: m[k] for k in ("id", "model", "base_url", "secret")}
                      | {"system_as_user": str(m.get("system_as_user", False)).lower()}
                      for m in chosen]}
line = "matrix=" + json.dumps(matrix, separators=(",", ":"))
print(line)
out = os.environ.get("GITHUB_OUTPUT")
if out:
    with open(out, "a") as f:
        f.write(line + "\n")

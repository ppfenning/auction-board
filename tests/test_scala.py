"""The harness's check arm runs `pytest -q`; this repository is Scala, so the
one pytest test drives the project's own test suite and reports its result."""
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def test_scala_suite_passes():
    run = subprocess.run(["scala-cli", "test", "."], cwd=ROOT, capture_output=True, text=True)
    # Show the per-suite munit lines so `pytest -q` output names what ran.
    print("\n".join(line for line in run.stdout.splitlines() if "Test run" in line or "failed" in line))
    assert run.returncode == 0, (run.stdout[-4000:] + run.stderr[-4000:])

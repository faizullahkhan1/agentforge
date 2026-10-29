# Evaluation results

The evals run the real agents (real OpenAI calls) on samples with a known answer key, several times, and score every run. They're opt-in, because they cost money and LLM output varies:

```bash
mvn -Peval test -pl pr-reviewer -Deval.runs=3 -Deval.modes=single,team-nojudge,team
mvn -Peval test -pl invoice-processor -Deval.runs=3
```

Full reports are written to `eval-results/` (git-ignored). This page keeps the baseline to compare future changes against. The answer keys are in [samples/eval/](../samples/eval/).

## Baseline: 2026-10-29, 3 runs each

Models: specialists, extractor and categorizer `gpt-4o-mini`; judge `gpt-4.1`. Cost of the whole run: roughly 20 cents.

### PR reviewer

Two samples: [UserService.java](../samples/UserService.java) with 7 known problems, and [CleanUserService.java](../samples/eval/CleanUserService.java), the same features written carefully, so every MEDIUM+ finding on it is a false positive.

| Mode | Recall on buggy code | Noise on buggy code (findings not in the key, per run) | False positives on clean code (MEDIUM+, per run) | Avg time (buggy) |
|---|---|---|---|---|
| `single`: one generalist agent | 43% | 0.0 | 1.0 | 4.0 s |
| `team-nojudge`: 3 specialists | **90%** | 2.0 | **3.7** | 4.6 s |
| `team`: 3 specialists + judge | 86% | **0.0** | **0.7** | 6.0 s |

Per known problem (found in N of 3 runs):

| Known problem | single | team-nojudge | team |
|---|---|---|---|
| hardcoded-password | 3/3 | 3/3 | 3/3 |
| sql-injection | 3/3 | 3/3 | 3/3 |
| null-connection | 0/3 | 3/3 | 3/3 |
| n-plus-one-queries | 0/3 | 3/3 | 3/3 |
| resource-leak | 0/3 | 3/3 | 3/3 |
| division-by-zero | 3/3 | 3/3 | 3/3 |
| integer-division | 0/3 | 1/3 | 0/3 |

**What this shows:**
- **Specialists double recall** (43% → 86–90%). The generalist reliably finds only the obvious problems.
- **Specialists alone are noisy.** On *clean* code they raised 4.3 findings per run, mostly invented: "N+1 query" on a single `IN (...)` query, "SQL injection" on placeholder-only concatenation, "division by zero" right after an `isEmpty()` check.
- **The judge removes most of that noise** (clean-code false positives 3.7 → 0.7, buggy-code noise 2.0 → 0) and costs only a little recall. The one false positive it kept is the "N+1 query" claim on clean code (2/3 runs).
- **Weak spot: integer division** (`total / users.size()` with `int`s) is almost never reported, even though it's on the bug agent's checklist.
- The single agent's clean-code false positive is a CRITICAL "SQL injection" (3/3 runs), so being quiet doesn't mean being precise.

### Invoice processor

| Invoice | Values read correctly | Categories correct | Planted error found | Runs with misreads | Caught | **Silent** |
|---|---|---|---|---|---|---|
| `cloudnest-invoice.pdf` (text) | 100% | 100% | n/a | 0/3 | n/a | 0 |
| `printhub-invoice.pdf` (text) | 100% | 100% | 3/3 | 0/3 | n/a | 0 |
| `cloudnest-invoice-photo.jpg` (vision) | 94% | 100% | n/a | 3/3 | 3/3 | 0 |
| `printhub-invoice-scanned.pdf` (vision) | 94% | 100% | 3/3 | 3/3 | 3/3 | 0 |

**Silent misreads: 0 of 12 runs.**

**What this shows:**
- **Text extraction is exact**, including German number and date formats.
- **Vision makes the same misread every run** (temperature 0): quantity 5 read as 0.5 on the photo, and year 2026 read as 2022 on the scan. Both are **always caught** by the Java checks (line math and the payment-term plausibility check), so the invoice goes to review instead of being booked wrong.
- The photo's status is "wrong" (NEEDS_REVIEW instead of READY) in all 3 runs. That's the intended behaviour after a misread: the true invoice is fine, but what was *read* isn't.
- Categories were right in every run.

## Change 1: integer division (2026-10-29, 5 runs)

**Diagnosis.** Calling the bug agent directly (no dedupe, no judge) showed it never mentioned integer division: it reported only "division by zero" for line 37, in 3 of 3 runs. It also exposed a latent bug in our own code: `dedupe()` kept one finding per line + category, so even a correct "integer division" finding would have been dropped as a duplicate of "division by zero" (both BUG, line 37).

**Fix.**
1. `dedupe()` now only merges findings with the same line and category **and similar titles** (at least half the meaningful words shared).
2. The bug agent's prompt explains integer division concretely, and says that different bugs on one line are separate findings.

Probing the bug agent again: integer division was reported as its own finding in 3 of 3 runs.

**Result: old vs new code, same conditions** (mode `team`, 5 runs each, back to back; the old code was restored temporarily with `git stash` for its run):

| | Old code | With change 1 |
|---|---|---|
| Recall on buggy code | 89% (integer division 1/5) | **100%** (integer division 5/5) |
| Noise on buggy code (per run) | 0.4 | 0.4 |
| False positives on clean code (MEDIUM+, per run) | 1.0 | 2.0 |

What the clean-code false positives were, run by run:

| Finding on `CleanUserService.java` | Old code | With change 1 |
|---|---|---|
| "N+1 query" on line 37 (it's one `IN (...)` query) | 5/5 | 5/5 |
| "NullPointerException if `ids` is null" on line 38 | 0/5 | 5/5 |

**Conclusion.** The precision drop is real, not noise, and it is exactly **one new finding**: "`ids` could be null" now appears in every run. It's debatable rather than plainly wrong (the method really doesn't guard against a null list, but most code treats null collections as the caller's mistake). The clear false positive, "N+1 query", was there before and is unchanged. Noise on the buggy code didn't change.

**Decision: keep the change.** It turns a real bug that was missed 4 times out of 5 into one found every time, at the cost of one debatable MEDIUM comment on clean code.

(A first, 3-run comparison had suggested 0.7 → 2.0. The 5-run back-to-back comparison above replaces it.)

**Not done on purpose:** telling the judge "a single `IN (...)` query is not N+1". That would fix this one sample without teaching the system anything general, which is overfitting to the eval. Precision work should start by adding more clean and buggy samples.

## Ideas this baseline suggests

- Try a stronger model for the bug agent only, to see whether integer division gets found, and re-run to compare.
- Add more invoice samples (handwritten, multi-page, credit notes) before trusting the "0 silent" result: 4 files is a small sample.
- Some "noise" findings are arguable rather than wrong (e.g. "returning null" from `findUser` on the buggy sample). The key could list them as optional, so they're neither credited nor penalised.

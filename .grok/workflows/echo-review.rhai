let meta = #{
    name: "echo-review",
    description: "Review an ECHOAndroid diff for module-boundary, correctness, and CI-contract issues",
    when_to_use: "Use before a PR or after a coding pass to review the working tree against AGENTS.md",
    phases: [
        #{ title: "Review", detail: "parallel reviewers: modules, correctness, CI contract" },
        #{ title: "Verify", detail: "one skeptic per finding" },
    ],
};

const MAX_FINDINGS = 10;
const MAX_DIFF = 24000;

let findings_schema = #{
    "type": "object",
    "required": ["findings"],
    "properties": #{
        "findings": #{
            "type": "array",
            "maxItems": 8,
            "items": #{
                "type": "object",
                "required": ["file", "issue"],
                "properties": #{
                    "file": #{ "type": "string" },
                    "issue": #{ "type": "string" },
                },
            },
        },
    },
};

let verdict_schema = #{
    "type": "object",
    "required": ["real", "reason", "evidence"],
    "properties": #{
        "real": #{ "type": "boolean" },
        "reason": #{ "type": "string" },
        "evidence": #{ "type": "string" },
    },
};

fn as_text(x) {
    if type_of(x) == "string" { return x; }
    if x == () { return ""; }
    json_encode(x)
}

fn in_repo_scope(file) {
    if type_of(file) != "string" { return false; }
    if file.contains("..") { return false; }
    if file.contains("app/") { return true; }
    if file.contains("core/") { return true; }
    if file.contains("feature/") { return true; }
    if file.contains("gradle/") { return true; }
    if file.contains("AGENTS.md") { return true; }
    if file.contains("settings.gradle") { return true; }
    if file.contains("build.gradle") { return true; }
    if file.contains(".github/") { return true; }
    if file.contains(".grok/") { return true; }
    false
}

fn take_n(items, n) {
    let out = [];
    let i = 0;
    for x in items {
        if i >= n { break; }
        out.push(x);
        i += 1;
    }
    out
}

let base = "HEAD";
if args != () && args.base != () && as_text(args.base) != "" {
    base = as_text(args.base);
}

let diff = git_diff_since(base);
if type_of(diff) != "string" { diff = ""; }
if diff == "" {
    let empty_path = write_scratch_file("report.md", "# echo-review\n\nNo diff since " + base + ".\n");
    complete(#{ path: empty_path, summary: "No diff to review.", base: base, confirmed: [] });
}

if diff.len() > MAX_DIFF {
    log("Diff truncated from " + diff.len().to_string() + " chars");
    diff = diff.sub_string(0, MAX_DIFF);
}
let diff_json = json_encode(diff);
let base_json = json_encode(base);

phase("Review");
let rules = " You MUST use git diff / read_file / grep on the real tree. Do not answer from memory.";
rules += " Read AGENTS.md and gradle/allowed-module-graph.txt when judging module placement.";
rules += " Report at most 8 {file, issue}. Empty findings is valid only after you inspected the diff.";

let p_modules = "Review this ECHOAndroid diff for MODULE BOUNDARY violations.";
p_modules += " Flag: feature→feature or core→feature project() deps; feature depending on :core:data/:core:playback/:core:connect/:core:lyrics/:core:usb-audio;";
p_modules += " new screens under app/src/main/java/app/echo/android/ui/; copied models; missing gradle/allowed-module-graph.txt or AGENTS.md updates.";
p_modules += rules;
p_modules += " Base ref JSON: " + base_json + " Diff JSON: " + diff_json;

let p_correct = "Review this ECHOAndroid diff for CORRECTNESS bugs: playback, library scan, Echo Link, lyrics, USB exclusive, Room, lifecycle leaks, broken state wiring from :app into feature UI.";
p_correct += rules;
p_correct += " Base ref JSON: " + base_json + " Diff JSON: " + diff_json;

let p_ci = "Review this ECHOAndroid diff for CI CONTRACT and test gaps: checkModules would fail, new behavior without a JVM unit test, secrets in source, AGENTS.md gitignored, gradlew not executable.";
p_ci += rules;
p_ci += " Base ref JSON: " + base_json + " Diff JSON: " + diff_json;

let reviews = parallel([
    #{ prompt: p_modules, label: "review-modules", capability_mode: "read-only", output_schema: findings_schema },
    #{ prompt: p_correct, label: "review-correctness", capability_mode: "read-only", output_schema: findings_schema },
    #{ prompt: p_ci, label: "review-ci", capability_mode: "read-only", output_schema: findings_schema },
]);

let raw = [];
for r in reviews {
    if r != () && r.success && r.output != () && r.output.findings != () {
        for f in r.output.findings {
            if f != () && in_repo_scope(f.file) { raw.push(f); }
            else if f != () { log("Dropped out-of-scope finding: " + as_text(f.file)); }
        }
    }
}
if raw.len() > MAX_FINDINGS {
    log("Truncated findings from " + raw.len().to_string() + " to " + MAX_FINDINGS.to_string());
    raw = take_n(raw, MAX_FINDINGS);
}

if raw.len() == 0 {
    let ok = "# echo-review\n\nNo in-scope findings after three reviewers on diff since " + base + ".\n";
    let ok_path = write_scratch_file("report.md", ok);
    complete(#{ path: ok_path, summary: "No findings.", base: base, confirmed: [] });
}

phase("Verify");
let skeptics = [];
let i = 0;
for f in raw {
    let sp = "Adversarially verify this ECHOAndroid review finding by reading the working tree / shipped code.";
    sp += " Set real=true only with concrete evidence you independently inspected. Otherwise real=false.";
    sp += " Finding JSON: " + json_encode(f);
    skeptics.push(#{
        prompt: sp,
        label: "verify-" + i.to_string(),
        capability_mode: "read-only",
        output_schema: verdict_schema,
    });
    i += 1;
}
let verdicts = parallel(skeptics);

let confirmed = [];
let k = 0;
for v in verdicts {
    if v != () && v.success && v.output != () && v.output.real == true
        && as_text(v.output.evidence) != "" {
        confirmed.push(raw[k]);
    }
    k += 1;
}
log(confirmed.len().to_string() + "/" + raw.len().to_string() + " findings survived verification");

let report = "# echo-review\n\n";
report += "- base: " + base + "\n";
report += "- raw_findings: " + raw.len().to_string() + "\n";
report += "- confirmed: " + confirmed.len().to_string() + "\n\n";
report += "## Confirmed\n\n";
if confirmed.len() == 0 { report += "(none)\n"; }
for c in confirmed {
    report += "- `" + as_text(c.file) + "`: " + as_text(c.issue) + "\n";
}
report += "\n## Unverified raw findings\n\n";
for f in raw {
    report += "- `" + as_text(f.file) + "`: " + as_text(f.issue) + "\n";
}
let report_path = write_scratch_file("report.md", report);

complete(#{
    path: report_path,
    summary: confirmed.len().to_string() + " confirmed findings since " + base,
    base: base,
    confirmed: confirmed,
});

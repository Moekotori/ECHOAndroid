let meta = #{
    name: "echo-review",
    description: "One-pass review of an ECHOAndroid diff",
    when_to_use: "Use before a PR or after a coding pass",
    phases: [
        #{ title: "Review", detail: "read the diff and AGENTS.md" },
    ],
};

const MAX_DIFF = 20000;

let review_schema = #{
    "type": "object",
    "required": ["ok", "notes", "findings"],
    "properties": #{
        "ok": #{ "type": "boolean" },
        "notes": #{ "type": "string" },
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

fn as_text(x) {
    if type_of(x) == "string" { return x; }
    if x == () { return ""; }
    json_encode(x)
}

let base = "HEAD";
if args != () && args.base != () && as_text(args.base) != "" {
    base = as_text(args.base);
}

let diff = git_diff_since(base);
if type_of(diff) != "string" { diff = ""; }
if diff == "" {
    let empty_path = write_scratch_file("report.md", "# echo-review\n\nNo diff since " + base + ".\n");
    complete(#{ path: empty_path, summary: "No diff to review.", base: base, ok: true });
}
if diff.len() > MAX_DIFF {
    log("Diff truncated from " + diff.len().to_string() + " chars");
    diff = diff.sub_string(0, MAX_DIFF);
}

phase("Review");
let prompt = "Review this ECHOAndroid diff. Use git diff / read_file / grep; do not answer from memory.";
prompt += " Read AGENTS.md. Focus on real bugs and module-graph mistakes";
prompt += " (feature→feature, core→feature, depending on :app). Skip style nits.";
prompt += " ok=true if the diff is fine to ship. At most 8 findings.";
prompt += " Base JSON: " + json_encode(base);
prompt += " Diff JSON: " + json_encode(diff);
let review = agent(prompt, #{
    label: "review",
    capability_mode: "read-only",
    output_schema: review_schema,
});

let ok = false;
let notes = "reviewer did not return a usable result";
let findings = [];
if review != () && review.success && review.output != () {
    ok = review.output.ok == true;
    notes = as_text(review.output.notes);
    if review.output.findings != () { findings = review.output.findings; }
}

let report = "# echo-review\n\n";
report += "- base: " + base + "\n";
report += "- ok: " + ok.to_string() + "\n";
report += "- notes: " + notes + "\n\n";
report += "## Findings\n\n";
if findings.len() == 0 { report += "(none)\n"; }
for f in findings {
    report += "- `" + as_text(f.file) + "`: " + as_text(f.issue) + "\n";
}
let report_path = write_scratch_file("report.md", report);

complete(#{
    path: report_path,
    summary: notes,
    base: base,
    ok: ok,
    findings: findings,
});

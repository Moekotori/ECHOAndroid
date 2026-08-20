let meta = #{
    name: "echo-dev",
    description: "Plan and implement an ECHOAndroid change on the correct modules, then verify the module graph",
    when_to_use: "Use when implementing a feature, bugfix, or refactor in this Android multi-module repo",
    phases: [
        #{ title: "Scope", detail: "pick modules and layer order from AGENTS.md" },
        #{ title: "Implement", detail: "edit the parent workspace in layer order" },
        #{ title: "Verify", detail: "module graph, task completeness, Gradle checkModules" },
        #{ title: "Fix", detail: "one pass on confirmed issues" },
    ],
};

const MAX_MODULES = 8;
const MAX_ISSUES = 8;
const MAX_DIFF = 24000;

let plan_schema = #{
    "type": "object",
    "required": ["summary", "modules", "layer_order", "notes"],
    "properties": #{
        "summary": #{ "type": "string" },
        "modules": #{
            "type": "array",
            "maxItems": 8,
            "items": #{ "type": "string" },
        },
        "layer_order": #{
            "type": "array",
            "maxItems": 8,
            "items": #{ "type": "string" },
        },
        "notes": #{ "type": "string" },
    },
};

let implement_schema = #{
    "type": "object",
    "required": ["done", "summary", "touched"],
    "properties": #{
        "done": #{ "type": "boolean" },
        "summary": #{ "type": "string" },
        "touched": #{
            "type": "array",
            "maxItems": 24,
            "items": #{ "type": "string" },
        },
    },
};

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

let gradle_schema = #{
    "type": "object",
    "required": ["ok", "summary"],
    "properties": #{
        "ok": #{ "type": "boolean" },
        "summary": #{ "type": "string" },
    },
};

fn as_text(x) {
    if type_of(x) == "string" { return x; }
    if x == () { return ""; }
    json_encode(x)
}

fn is_echo_module(p) {
    if type_of(p) != "string" { return false; }
    if p == ":app" { return true; }
    if p.len() >= 6 && p.sub_string(0, 6) == ":core:" { return true; }
    if p.len() >= 9 && p.sub_string(0, 9) == ":feature:" { return true; }
    false
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

let task = ();
if args != () { task = args.task; }
if task == () || as_text(task) == "" {
    pause("verification", "Pass args.task — the change to implement (one sentence is enough).");
}
let task_text = as_text(task);
let task_json = json_encode(task_text);

phase("Scope");
let scope_prompt = "You are scoping a change for ECHOAndroid, an Android Gradle multi-module music player.";
scope_prompt += " The user task JSON is: " + task_json + "\n";
scope_prompt += "You MUST use read_file/grep/list_dir before answering. Read AGENTS.md, gradle/allowed-module-graph.txt,";
scope_prompt += " settings.gradle.kts, and the existing code you would touch. Do not answer from memory.\n";
scope_prompt += "Rules: :app is composition root; :feature:* is Compose UI only (depends only on :core:model and :core:design);";
scope_prompt += " :core:* holds implementation; :core:model has no project deps; no feature→feature; no core→feature;";
scope_prompt += " do not add new UI under app/src/main/java/app/echo/android/ui/.\n";
scope_prompt += "Return modules as Gradle paths like :core:data or :feature:player.";
scope_prompt += " layer_order must be a subset of [core:model, core, feature, app] in that sequence.";
scope_prompt += " An empty modules list is valid only after you inspected the repo and the task needs no code change.";
let plan = agent(scope_prompt, #{
    label: "scope",
    capability_mode: "read-only",
    output_schema: plan_schema,
});

let modules = [];
let layer_order = [];
let plan_summary = "";
let plan_notes = "";
if plan != () && plan.success && plan.output != () {
    plan_summary = as_text(plan.output.summary);
    plan_notes = as_text(plan.output.notes);
    if plan.output.modules != () {
        for m in plan.output.modules {
            if is_echo_module(m) { modules.push(m); }
            else { log("Dropped out-of-scope module from plan: " + as_text(m)); }
        }
    }
    if plan.output.layer_order != () { layer_order = plan.output.layer_order; }
}
if modules.len() > MAX_MODULES {
    log("Truncated modules from " + modules.len().to_string() + " to " + MAX_MODULES.to_string());
    modules = take_n(modules, MAX_MODULES);
}
log("Scope modules: " + json_encode(modules));

phase("Implement");
let impl_prompt = "Implement the ECHOAndroid task in the parent workspace. Do not use a private worktree.";
impl_prompt += " Task JSON: " + task_json + "\n";
impl_prompt += " Scoped modules JSON: " + json_encode(modules) + "\n";
impl_prompt += " Layer order JSON: " + json_encode(layer_order) + "\n";
impl_prompt += " Plan summary JSON: " + json_encode(plan_summary) + "\n";
impl_prompt += " Plan notes JSON: " + json_encode(plan_notes) + "\n";
impl_prompt += "You MUST read AGENTS.md, gradle/allowed-module-graph.txt, and the current source before editing.";
impl_prompt += " Follow existing Kotlin/Compose patterns. Smallest change that solves the task.\n";
impl_prompt += "Layer order: shared types in :core:model first, then core implementation, then :feature:* UI, then :app wiring.";
impl_prompt += " Feature screens take core:model state and lambdas — do not query Room or MediaController from a feature module.";
impl_prompt += " If you add a module, update settings.gradle.kts, gradle/allowed-module-graph.txt, AGENTS.md, and :app dependencies together.\n";
impl_prompt += "Do not commit. Do not add feature→feature or core→feature project() deps.";
impl_prompt += " Do not add new screens under app/src/main/java/app/echo/android/ui/.";
impl_prompt += " After edits, if JAVA is available, run the Gradle wrapper checkModules";
impl_prompt += " (gradlew.bat on Windows, ./gradlew on Unix) with --no-configuration-cache.";
impl_prompt += " Set JAVA_HOME to F:\\\\AndroidDev\\\\jdk-21 on this machine if java is missing from PATH.";
impl_prompt += " Return done=true only if the code change for the task is in the tree.";
let impl = agent(impl_prompt, #{
    label: "implement",
    capability_mode: "all",
    output_schema: implement_schema,
});

let impl_done = false;
let impl_summary = "implementer did not return a usable result";
let touched = [];
if impl != () && impl.success && impl.output != () {
    impl_done = impl.output.done == true;
    impl_summary = as_text(impl.output.summary);
    if impl.output.touched != () { touched = impl.output.touched; }
}

phase("Verify");
let diff = git_diff_since("HEAD");
if type_of(diff) != "string" { diff = ""; }
if diff.len() > MAX_DIFF {
    log("Verify diff truncated from " + diff.len().to_string() + " chars");
    diff = diff.sub_string(0, MAX_DIFF);
}
let diff_json = json_encode(diff);

let module_prompt = "Adversarially review this ECHOAndroid working-tree diff for MODULE BOUNDARY violations.";
module_prompt += " Read AGENTS.md and gradle/allowed-module-graph.txt, then read the cited files. Do not answer from memory.";
module_prompt += " Fail a finding unless you can point at a concrete project() edge, a feature importing the wrong core,";
module_prompt += " new UI under app/.../ui/, or a missing allowed-module-graph.txt update.\n";
module_prompt += " Diff JSON: " + diff_json + "\n";
module_prompt += " Report at most 8 {file, issue}. Empty findings is valid only after you inspected the diff (or confirmed it is empty).";

let task_prompt = "Review whether the working-tree diff actually implements the task and does not regress ECHOAndroid.";
task_prompt += " Use git diff, read_file, and grep on the real files. Do not answer from memory.";
task_prompt += " Task JSON: " + task_json + "\n";
task_prompt += " Diff JSON: " + diff_json + "\n";
task_prompt += " Report at most 8 concrete {file, issue} bugs or missing pieces. Empty list is valid only after inspection.";

let gradle_prompt = "Run ECHOAndroid module-graph verification with the Gradle wrapper.";
gradle_prompt += " Windows: gradlew.bat checkModules --no-configuration-cache";
gradle_prompt += " Unix: ./gradlew checkModules --no-configuration-cache";
gradle_prompt += " If java is missing, set JAVA_HOME to F:\\\\AndroidDev\\\\jdk-21 and retry.";
gradle_prompt += " Do not run a full assemble unless checkModules is not available.";
gradle_prompt += " ok=true only if the command exits 0. Summarize the output either way.";

let vpanel = parallel([
    #{ prompt: module_prompt, label: "verify-modules", capability_mode: "read-only", output_schema: findings_schema },
    #{ prompt: task_prompt, label: "verify-task", capability_mode: "read-only", output_schema: findings_schema },
    #{ prompt: gradle_prompt, label: "verify-gradle", capability_mode: "execute", output_schema: gradle_schema },
]);

let raw_findings = [];
let gradle_ok = false;
let gradle_summary = "gradle check did not run";
let vi = 0;
for r in vpanel {
    if vi == 2 {
        if r != () && r.success && r.output != () {
            gradle_ok = r.output.ok == true;
            gradle_summary = as_text(r.output.summary);
        }
    } else if r != () && r.success && r.output != () && r.output.findings != () {
        for f in r.output.findings {
            if f != () && in_repo_scope(f.file) { raw_findings.push(f); }
            else if f != () { log("Dropped out-of-scope finding: " + as_text(f.file)); }
        }
    }
    vi += 1;
}
if raw_findings.len() > MAX_ISSUES {
    log("Truncated findings from " + raw_findings.len().to_string() + " to " + MAX_ISSUES.to_string());
    raw_findings = take_n(raw_findings, MAX_ISSUES);
}

let confirmed = [];
if raw_findings.len() > 0 {
    let skeptics = [];
    let fi = 0;
    for f in raw_findings {
        let sp = "Adversarially verify this ECHOAndroid review finding by reading the shipped/working tree code.";
        sp += " Set real=true only with concrete evidence you independently inspected. Default real=false.";
        sp += " Finding JSON: " + json_encode(f);
        skeptics.push(#{
            prompt: sp,
            label: "skeptic-" + fi.to_string(),
            capability_mode: "read-only",
            output_schema: verdict_schema,
        });
        fi += 1;
    }
    let verdicts = parallel(skeptics);
    let k = 0;
    for v in verdicts {
        if v != () && v.success && v.output != () && v.output.real == true
            && as_text(v.output.evidence) != "" {
            confirmed.push(raw_findings[k]);
        }
        k += 1;
    }
    log(confirmed.len().to_string() + "/" + raw_findings.len().to_string() + " findings survived verification");
}

phase("Fix");
let fix_summary = "no confirmed issues";
if confirmed.len() > 0 {
    let fix_prompt = "Fix the confirmed issues in the ECHOAndroid parent workspace. Smallest change. Do not commit.";
    fix_prompt += " Keep module rules in AGENTS.md. Confirmed issues JSON: " + json_encode(confirmed);
    let fix = agent(fix_prompt, #{
        label: "fix",
        capability_mode: "all",
        output_schema: implement_schema,
    });
    if fix != () && fix.success && fix.output != () {
        fix_summary = as_text(fix.output.summary);
        if fix.output.touched != () {
            for t in fix.output.touched { touched.push(t); }
        }
    } else {
        fix_summary = "fix-up agent failed; confirmed issues remain";
    }
}

let report = "# echo-dev\n\n";
report += "- task: " + task_text + "\n";
report += "- modules: " + json_encode(modules) + "\n";
report += "- implement_done: " + impl_done.to_string() + "\n";
report += "- implement: " + impl_summary + "\n";
report += "- gradle_ok: " + gradle_ok.to_string() + "\n";
report += "- gradle: " + gradle_summary + "\n";
report += "- fix: " + fix_summary + "\n";
report += "- confirmed_issues: " + json_encode(confirmed) + "\n";
report += "- touched: " + json_encode(touched) + "\n";
let report_path = write_scratch_file("report.md", report);

complete(#{
    path: report_path,
    summary: impl_summary,
    modules: modules,
    implement_done: impl_done,
    gradle_ok: gradle_ok,
    confirmed: confirmed,
});

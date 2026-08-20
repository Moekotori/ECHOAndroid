let meta = #{
    name: "echo-dev",
    description: "Implement one ECHOAndroid change on the right modules, then do a light check",
    when_to_use: "Use to implement a feature, bugfix, or small refactor in this repo",
    phases: [
        #{ title: "Implement", detail: "edit the workspace following AGENTS.md" },
        #{ title: "Check", detail: "one pass over the diff and checkModules" },
    ],
};

const MAX_DIFF = 20000;

let implement_schema = #{
    "type": "object",
    "required": ["done", "summary", "touched"],
    "properties": #{
        "done": #{ "type": "boolean" },
        "summary": #{ "type": "string" },
        "touched": #{
            "type": "array",
            "maxItems": 16,
            "items": #{ "type": "string" },
        },
    },
};

let check_schema = #{
    "type": "object",
    "required": ["ok", "notes"],
    "properties": #{
        "ok": #{ "type": "boolean" },
        "notes": #{ "type": "string" },
    },
};

fn as_text(x) {
    if type_of(x) == "string" { return x; }
    if x == () { return ""; }
    json_encode(x)
}

let task = ();
if args != () { task = args.task; }
if task == () || as_text(task) == "" {
    pause("verification", "Pass args.task — one sentence for the change to make.");
}
let task_json = json_encode(as_text(task));

phase("Implement");
let impl_prompt = "Implement this ECHOAndroid task in the parent workspace. Do not commit.";
impl_prompt += " Task JSON: " + task_json + "\n";
impl_prompt += "Read AGENTS.md, gradle/allowed-module-graph.txt, and the current code before editing.";
impl_prompt += " Follow existing Kotlin/Compose style. Smallest change that solves the task.";
impl_prompt += " Prefer feature UI in :feature:*, wiring in :app, shared types in :core:model.";
impl_prompt += " Do not add feature→feature or core→feature project() dependencies.";
impl_prompt += " If you change project() deps, update gradle/allowed-module-graph.txt.";
impl_prompt += " If the Gradle wrapper can run, prefer gradlew.bat / ./gradlew checkModules --no-configuration-cache.";
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

phase("Check");
let diff = git_diff_since("HEAD");
if type_of(diff) != "string" { diff = ""; }
if diff.len() > MAX_DIFF { diff = diff.sub_string(0, MAX_DIFF); }

let check_prompt = "Light-check the ECHOAndroid working tree after an implementation.";
check_prompt += " Read the diff and AGENTS.md. Optionally run checkModules.";
check_prompt += " ok=true if there is no obvious module-graph break or missing piece of the task.";
check_prompt += " Do not nitpick style. Task JSON: " + task_json;
check_prompt += " Diff JSON: " + json_encode(diff);
check_prompt += " Implementer summary JSON: " + json_encode(impl_summary);
let check = agent(check_prompt, #{
    label: "check",
    capability_mode: "execute",
    output_schema: check_schema,
});

let check_ok = false;
let check_notes = "";
if check != () && check.success && check.output != () {
    check_ok = check.output.ok == true;
    check_notes = as_text(check.output.notes);
}

let report = "# echo-dev\n\n";
report += "- task: " + as_text(task) + "\n";
report += "- done: " + impl_done.to_string() + "\n";
report += "- implement: " + impl_summary + "\n";
report += "- check_ok: " + check_ok.to_string() + "\n";
report += "- check: " + check_notes + "\n";
report += "- touched: " + json_encode(touched) + "\n";
let report_path = write_scratch_file("report.md", report);

complete(#{
    path: report_path,
    summary: impl_summary,
    implement_done: impl_done,
    check_ok: check_ok,
});

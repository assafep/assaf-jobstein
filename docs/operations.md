# Operating Jobstein

Use these commands after [onboarding](onboarding.md). Examples use the synthetic sandbox. For a configured live environment, select its name explicitly with `--env`. Use one host per live environment. Competing local administrative commands exit with code 4 when the environment lock is busy.

## Runtime lifecycle and configuration

Today each command starts the Jobstein runtime, performs one operation, and exits. Profile activation marks a profile eligible for use. It does not launch a background job. Start, stop, runtime status, scheduling and launch-time configuration belong here once a long-running runtime exists.

## Check an environment

```sh
jobstein environment list
jobstein environment current --env development
jobstein doctor --env sandbox
jobstein profile list --env sandbox
jobstein state status --env sandbox
```

Use `environment current` to confirm which environment a command will select and which Google Cloud project is bound to it. Use doctor for readiness, profile list for role slugs and activation status, and state status for the consistency of the local and stored profile indexes. Doctor checks settings, checkpoint integrity, integrations and active descriptors. On a live environment it makes validation requests. If a check fails, follow its diagnostic and rerun doctor. It leaves remote state and existing configuration permissions intact.

## Maintain profiles and resumes

Use `jobstein profile create --env sandbox --role "Product Manager"` to add a role with synthetic fixtures. Each role has its own resume history. In a live environment, supply a text-based PDF, consent to AI processing, and review the descriptor.

```sh
jobstein profile resume update --env sandbox --profile software.engineer
jobstein profile resume history --env sandbox --profile software.engineer
jobstein profile descriptor regenerate --env sandbox --profile software.engineer
jobstein profile descriptor show --env sandbox --profile software.engineer
jobstein profile activate --env sandbox --profile software.engineer --approve-descriptor
```

A changed PDF invalidates the previous descriptor. Approve activation only after reviewing the replacement. The sandbox example generates a synthetic PDF. For a supplied PDF, pass the same file with `--resume` on update and regeneration. In a live environment activation displays the descriptor and prompts for approval. To take a role out of use, run `jobstein profile deactivate` with its environment and profile slug.

## Change AI settings for one agent type

A named integration bundles a provider, model, options and credential references. This example changes which integration generates descriptors in the sandbox, checks the binding, then restores the default.

```sh
jobstein ai integration create --env sandbox --integration advanced --field model=mock-grok-advanced
jobstein ai agent configure --env sandbox --agent applicant-profile-generator --integration advanced
jobstein ai agent show --env sandbox --agent applicant-profile-generator
jobstein ai agent reset --env sandbox --agent applicant-profile-generator
jobstein ai integration remove --env sandbox --integration advanced
```

## Inspect and recover profile indexes

Run `jobstein state recovery inspect --env sandbox`. Choose the next action from the reported state.

| Result | Action |
| --- | --- |
| CLEAN | The indexes agree and there is no pending write. |
| RECOVERABLE | Preview with `state recovery resume`. Add `--apply` to save a private backup of both copies and the journal, then finish the verified update. |
| DIVERGED | There is no pending journal. Inspect both copies first. Preview `state reconcile --source local` or `--source remote` according to which copy should win. Add `--apply` to back up both indexes and replace the other copy. |
| CONFLICT | Preserve the environment and investigate the unexpected snapshot or identity before making any changes. |

Supply the same `--env` on each command. Run `jobstein state validate` with that environment after recovery. A `state backup` covers only the profile indexes. It does not back up resumes, descriptors, credentials or the rest of Drive.

## Find a command

Use `jobstein --help` for command groups and a group name such as `jobstein profile` to list its subcommands. The `environment`, `google`, `ai`, `user`, `profile`, and `state` groups let you administer individual parts of an environment.

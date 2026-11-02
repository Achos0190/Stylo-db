# Stylus haptics research

`stylus-haptics-db.json` collects what is known so far about driving a stylus's own haptic motor from an Android app. It covers the OnePlus Stylo 2 first, with Xiaomi, Lenovo, Huawei, Apple and Windows as references.

## Records

Each record has:

| Field | Meaning |
| --- | --- |
| `id` | Stable slug. The prefix shows the type: `src-`, `dev-`, `if-`, `c-`, `plan-`, `gap-` (and `repo-` for this repository). |
| `type` | `source`, `device`, `interface`, `concept`, `plan` or `gap` |
| `status` | How well the content has been checked (see below) |
| `priority` | `critical`, `high`, `medium` or `low` |
| `vendor`, `category` | Grouping |
| `url`, `links` | Main link and any extra `{label, url}` links |
| `summary`, `details` | One-sentence summary and a list of facts |
| `tags`, `related` | Keywords and IDs of related records |
| `basis` | Where the content came from and when it was checked |

## Status

- **verified**: checked on 2 Nov 2026 against a primary source, such as official documentation, a project's own page or the code in this repository.
- **unverified**: taken from earlier research notes, vendor specifications, community posts or background knowledge, and not yet checked.
- **inferred**: a conclusion drawn from verified facts. Treat it as a working hypothesis.
- **open**: unknown. A question to answer before building on it.

## Where to start

- `if-oplus-sdk-interface` and `if-oplus-call-sequence`: the service interface this app uses, as implemented here.
- `c-three-subsystems` and `c-feedback-models`: how pen input, the Bluetooth side channel and the pen's motor fit together.
- `if-windows-haptic-pen-device` and `if-hid-haptics-page`: the only fully public, vendor-neutral haptic pen protocol found, useful as a model for a common API.
- `c-pen-attach-handshake`: how the pen's magnetic charger hands its Bluetooth address to the tablet, and why that breaks after a regional ROM conversion. Full write-up in [`pen-attach-handshake.md`](pen-attach-handshake.md).
- `gap-*`: what is still unknown.

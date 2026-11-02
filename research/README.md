# Stylus haptics research

Everything gathered so far about driving a stylus's own haptic motor from an Android app. It covers the OnePlus Stylo 2 first, with Xiaomi, Lenovo, Huawei, Apple and Windows as references.

## How this fits into Stylo-db

This folder comes from a separate research track. It started on branch `ccr-1974d431-9lzw9a` of `Achos0190/stylo2-haptics` and moved here on 2 Nov 2026. The rest of the repository holds the implementation track:

| Location | Holds |
| --- | --- |
| `research/` (this folder) | The structured research database, the pen attach-handshake and firmware analysis, reference copies of community fix modules, and a pickup prompt for reading firmware off the tablet |
| `stylo2/` and `stylo2/docs/` | The Rust workspace and its documents A–J: reconnaissance, feasibility, protocol, plans, evidence ledger, capture plan, `MISSION.md` and `LEGAL.md` |
| `android/` | Proof-of-concept app, BLE inspector and Kotlin bridge |
| `app/` | The original accessory-service client with the reproduced AIDL |
| `PICKUP.md` | Handoff for on-device haptics testing (Routes A and B) |

The two tracks overlap: `stylo2/docs/I-evidence-ledger.md` and this database both track claims and their status. Keep them consistent when a finding changes either one.

Rules differ between the two handoffs. `PICKUP.md` excludes root for the implementation work. [`docs/local-session-pickup.md`](docs/local-session-pickup.md) is a read-only firmware read-out in which root-only steps are optional and need the owner's explicit OK.

## Layout

```
research/
├── data/                    The database: one JSON file per record type
│   ├── meta.json            Title, status legend, type names, file map
│   ├── sources.json         Links and documents
│   ├── devices.json         Pens and tablets
│   ├── interfaces.json      APIs, services, drivers, protocols, firmware
│   ├── concepts.json        How the pieces work
│   ├── plans.json           Architecture, assessments, research order
│   └── gaps.json            Open questions
├── schema/
│   └── record.schema.json   JSON Schema for one record
├── tools/
│   ├── validate.py          Checks every record and cross-reference
│   ├── build.py             Combined file and per-record documents, in build/
│   └── fwinfo.py            Inspects cps8601_firmware.bin files (hash, known build, 8051 vectors, strings)
├── site/
│   └── stylus-haptics-db.html    Source of the searchable database page (runs on claude.ai)
├── docs/
│   ├── pen-attach-handshake.md   Pen charger handshake and the regional-ROM stylus fix
│   └── local-session-pickup.md   Instructions for a local session with the tablet connected
└── reference/
    ├── fix-odm-oplus-pad2pro/      jjhitel's module scripts (GPL-3.0), unmodified, with PROVENANCE.md
    └── oplus-global-pen-enabler/   MANIFEST.md: cxzlw's firmware files by path and SHA-256 (binaries not stored)
```

`build/` is generated, and `local/` holds files pulled from a tablet (firmware, logs). Git ignores both.

## Records

Each record has:

| Field | Meaning |
| --- | --- |
| `id` | Stable slug. Prefix by type: `src-` or `repo-` (source), `dev-`, `if-`, `c-` (concept), `plan-`, `gap-` |
| `type` | `source`, `device`, `interface`, `concept`, `plan` or `gap`. Must match the file it is in |
| `title`, `summary`, `details` | Name, one-sentence summary, list of facts |
| `vendor`, `category` | Grouping |
| `status` | How well it has been checked (see below) |
| `priority` | `critical`, `high`, `medium` or `low` |
| `url`, `links` | Main link (or empty) and any extra `{label, url}` links |
| `tags` | Lowercase-kebab keywords |
| `related` | IDs of other records. Every ID must exist |
| `basis` | Where the content came from and when it was checked |
| `updated` | Date the record last changed |

### Status

- **verified**: checked on 2 Nov 2026 against a primary source, such as official documentation, a project's own page, an uploaded file, or the app code (`app/` in this repository, from `stylo2-haptics`).
- **unverified**: taken from earlier research notes, vendor specifications, community posts or background knowledge, and not yet checked.
- **inferred**: a conclusion drawn from verified facts. Treat it as a working hypothesis.
- **open**: unknown. A question to answer before building on it.

## Working with the data

Edit the file for the record's type, then:

```bash
python3 research/tools/validate.py            # errors exit non-zero
python3 research/tools/validate.py --verbose  # also lists one-way "related" links
python3 research/tools/build.py               # writes research/build/
```

Both tools use only the Python standard library.

The searchable database page (`site/stylus-haptics-db.html`) reads the same records from its own database on claude.ai. `build/seed/` holds them in the shape the page stores: one document per record, keyed by ID. Opened as a local file, the page shows a notice instead of records.

## Third-party material

- `reference/fix-odm-oplus-pad2pro/` is an unmodified copy of jjhitel's GPL-3.0 module scripts, kept for analysis. It is not an installable module.
- The CPS8601 firmware binaries are OnePlus firmware with no licence that allows redistribution, so only their paths and hashes are recorded.

## Where to start

- `if-oplus-sdk-interface` and `if-oplus-call-sequence`: the pen service interface this app uses.
- `c-three-subsystems` and `c-feedback-models`: how pen input, the Bluetooth link and the pen's motor fit together.
- `c-pen-attach-handshake` and [`docs/pen-attach-handshake.md`](docs/pen-attach-handshake.md): how the pen pairs, and why it stops working after a regional ROM conversion.
- `if-windows-haptic-pen-device` and `if-hid-haptics-page`: the only fully public, vendor-neutral haptic pen protocol found, useful as a model for a common API.
- `gaps.json`: what is still unknown.

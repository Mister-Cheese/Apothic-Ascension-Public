# Changelog

## 1.14.0-Beta — Equipment Interop I

- Added an explicit `item_state` compatibility domain.
- Reforge candidates now preserve item identity and foreign-owned component state.
- Unknown component owners are treated as foreign and preserved.
- Destructive reforge candidates are rejected before becoming Apex offers.
- Extended the audited Allthemodium compatibility band through 3.1.0.
- Added qualified equipment interoperability for Simply Swords and Silent Gear.
- Tetra remains intentionally unsupported on Minecraft 1.21.1 because no audited official
  artifact exists for that game version.

## 1.13.0-Beta — Cooperative Ownership

- Added version-aware specialist-provider ownership records.
- Added an independent `magic_resource` compatibility domain.
- Added cooperative ownership behavior for Ars 'n Spells and Iron's Apothic.
- Added audited coexistence handling for Apothic Category Compat, Extra Apoth Compat,
  and Fallen Gems & Affixes.
- Unsupported or out-of-band specialist providers continue to fail closed.

Earlier release notes are retained with their original release artifacts.

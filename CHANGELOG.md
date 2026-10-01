# Changelog

## 1.14.1-Beta

- Moved development-only validation code out of the distributed mod JAR. Gameplay is unchanged.
- Added MPL-2.0 source publication metadata and a public source repository.
- Kept the Beta 14 gameplay and equipment compatibility behavior unchanged.

## 1.14.0-Beta

- Added an explicit `item_state` compatibility domain.
- Reforge candidates now preserve item identity and third-party component state.
- Unknown component owners are treated as foreign and preserved.
- Destructive reforge candidates are rejected before they can become Apex offers.
- Extended the audited Allthemodium compatibility band through 3.1.0.
- Qualified equipment interoperability for Simply Swords 1.70.2-1.21.1 and Silent Gear 4.1.5.
- Tetra remains unqualified on Minecraft 1.21.1 because the audited release line does not provide
  an official 1.21.1 artifact.

## 1.13.0-Beta

- Added version-aware specialist-provider ownership records.
- Added an independent `magic_resource` compatibility domain.
- Added cooperative ownership behavior for Ars 'n Spells and Iron's Apothic.
- Added coexistence handling for Apothic Category Compat, Extra Apoth Compat, and Fallen Gems &
  Affixes without suppressing unrelated AA behavior.
- Unsupported or out-of-band specialist providers continue to fail closed.

Earlier release notes remain with their original release artifacts.

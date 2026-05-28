# Auto-Upgrade Rules

When the player equips a freshly-looted piece of gear, UWYG raises that piece's reinforcement level so it stays "at par" with the rest of the player's loadout, but never above the rules of its own upgrade path. This document is the ground-truth specification used by `EquipmentAutoUpgrader`, `WeaponUpgradeEquivalence` and `ArmorUpgradeEquivalence`.

There are **two independent comparison scales**:

| Scale                         | Range  | Used by                                                |
|-------------------------------|--------|--------------------------------------------------------|
| Equivalent **NORMAL** tier    | 0…15   | Melee weapons, shields, bows, reinforcing spell tools  |
| Equivalent **armor** tier     | 0…5    | Armor (head / chest / hands / legs)                    |

A weapon's `equivalentNormalTier` is never compared against an armor's `equivalentArmorTier`. They live on different ladders.

---

## 1. Peer groups (who compares against whom)

Auto-upgrade only triggers on `EQUIP_WEAPON` and `EQUIP_ARMOR`. The picked-up piece looks at the rest of the inventory and finds the **highest equivalent tier among peers**, then raises itself up to (but not above) that ceiling on its own path.

| Inventory item                                                                                                | Peer group                      | Auto-upgraded? |
|---------------------------------------------------------------------------------------------------------------|---------------------------------|----------------|
| Melee weapon                                                                                                  | `WEAPON`                        | Yes            |
| Shield                                                                                                        | `WEAPON`                        | Yes            |
| Ranged weapon — **bow / crossbow**                                                                            | `WEAPON`                        | Yes            |
| Ranged weapon — **arrow / bolt stack**                                                                        | `OTHER` (excluded)              | No             |
| Spell tool with upgrade path `UNIQUE` / `INFUSABLE` / `INFUSABLE_RESTRICTED` / `PYRO_FLAME` / `PYRO_ASCENDED` | `WEAPON`                        | Yes            |
| Spell tool with upgrade path `NONE` / `STANDARD_ARMOR` (Talisman etc.)                                        | `OTHER` (excluded)              | No             |
| Armor                                                                                                         | `ARMOR`                         | Yes            |
| Spell                                                                                                         | n/a — attuned to slot 1 instead | No             |
| Ring                                                                                                          | n/a — no upgrade path           | No             |

`WEAPON` is one shared pool: melee weapons, shields, bows, and reinforcing spell tools all see each other as peers. `ARMOR` is its own pool.

---

## 2. Weapon equivalent NORMAL tier (`equivalentNormalTier`, 0…15)

Every weapon upgrade path projects its `upgradeLevel` onto the 0…15 scale as follows.

### 2.1 `NONE` and `STANDARD_ARMOR`

Always projects to `0`.

### 2.2 `UNIQUE` (Twinkling Titanite) — max `upgradeLevel` 5

| `upgradeLevel` | `equivalentNormalTier` |
|----------------|-----------------------:|
| +0             |                      0 |
| +1             |                      3 |
| +2             |                      6 |
| +3             |                      9 |
| +4             |                     12 |
| +5             |                     15 |

### 2.3 `INFUSABLE` — full infusion set; `INFUSABLE_RESTRICTED` — reduced set

Each infusion path has its own `upgradeLevel` cap and its own offset on the
shared scale.

| Infusion path | Max `upgradeLevel` | `equivalentNormalTier` formula | Range produced | Available on `INFUSABLE_RESTRICTED` |
|---------------|--------------------|--------------------------------|----------------|-------------------------------------|
| `NORMAL`      | 15                 | tier = `upgradeLevel`          | 0…15           | Yes                                 |
| `MAGIC`       | 10                 | tier = 5 + `upgradeLevel`      | 5…15           | Yes                                 |
| `DIVINE`      | 10                 | tier = 5 + `upgradeLevel`      | 5…15           | Yes                                 |
| `FIRE`        | 10                 | tier = 5 + `upgradeLevel`      | 5…15           | Yes                                 |
| `LIGHTNING`   | 5                  | tier = 10 + `upgradeLevel`     | 10…15          | Yes                                 |
| `CRYSTAL`     | 5                  | tier = 10 + `upgradeLevel`     | 10…15          | Yes                                 |
| `RAW`         | 5                  | tier = 10 + `upgradeLevel`     | 10…15          | **No**                              |
| `ENCHANTED`   | 5                  | tier = 10 + `upgradeLevel`     | 10…15          | **No**                              |
| `OCCULT`      | 5                  | tier = 10 + `upgradeLevel`     | 10…15          | **No**                              |
| `CHAOS`       | 5                  | tier = 10 + `upgradeLevel`     | 10…15          | **No**                              |

Concrete examples:

```
NORMAL    +0  → 0     +1  → 1     +7  → 7     +15 → 15      (identity)
DIVINE    +0  → 5     +1  → 6     +5  → 10    +10 → 15
MAGIC     +0  → 5     +3  → 8                 +10 → 15
FIRE      +0  → 5     +7  → 12                +10 → 15
LIGHTNING +0  → 10    +1  → 11                +5  → 15
CRYSTAL   +0  → 10    +3  → 13                +5  → 15
OCCULT    +0  → 10                            +5  → 15
ENCHANTED +0  → 10                            +5  → 15
CHAOS     +0  → 10    +2  → 12                +5  → 15
RAW       +0  → 10    +4  → 14                +5  → 15
```

### 2.4 `PYRO_FLAME` — max `upgradeLevel` 15 (identity)

| `upgradeLevel` | `equivalentNormalTier` |
|----------------|-----------------------:|
| +0             |                      0 |
| +1             |                      1 |
| …              |                      … |
| +15            |                     15 |

### 2.5 `PYRO_ASCENDED` — max `upgradeLevel` 5 (same mapping as `UNIQUE`)

| `upgradeLevel` | `equivalentNormalTier` |
|----------------|-----------------------:|
| +0             |                      0 |
| +1             |                      3 |
| +2             |                      6 |
| +3             |                      9 |
| +4             |                     12 |
| +5             |                     15 |

---

## 3. Armor equivalent armor tier (`equivalentArmorTier`, 0…5)

### 3.1 `NONE`

Always projects to `0`.

### 3.2 `UNIQUE` (Twinkling armor) — max `upgradeLevel` 5 (identity)

| `upgradeLevel` | `equivalentArmorTier` |
|----------------|----------------------:|
| +0             |                     0 |
| +1             |                     1 |
| +2             |                     2 |
| +3             |                     3 |
| +4             |                     4 |
| +5             |                     5 |

### 3.3 `STANDARD_ARMOR` — max `upgradeLevel` 10 (banded)

| `upgradeLevel` band | `equivalentArmorTier` |
|---------------------|----------------------:|
| +0                  |                     0 |
| +1, +2              |                     1 |
| +3, +4              |                     2 |
| +5, +6              |                     3 |
| +7, +8, +9          |                     4 |
| +10                 |                     5 |

### 3.4 Inverse mapping for `STANDARD_ARMOR`

When the auto-upgrader needs to **encode** a target tier back to an item id, it picks the **highest** `upgradeLevel` inside the band (so the wearer keeps every cumulative bonus the band offers):

| Target `equivalentArmorTier` | Encoded `upgradeLevel` |
|------------------------------|-----------------------:|
| 0                            |                     +0 |
| 1                            |                     +2 |
| 2                            |                     +4 |
| 3                            |                     +6 |
| 4                            |                     +9 |
| 5                            |                    +10 |
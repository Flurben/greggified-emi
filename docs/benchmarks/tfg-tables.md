Runs: 3 without the mod, 3 with it.

| Scenario | EMI alone: mean of runs (range) ms | With Greggified EMI: mean of runs (range) ms | Change |
| --- | --- | --- | --- |
| frames: inventory, mouse idle | 3.945 (3.848–4.033) | 4.238 (4.186–4.295) | +7.4% |
| frames: inventory, hover index stack | 3.856 (3.758–3.915) | 4.050 (3.979–4.100) | +5.0% |
| frames: inventory, hover index stack, item zoom off | — | 3.738 (3.618–3.840) |  |
| frames: inventory, page through index | 4.667 (4.636–4.695) | 4.344 (4.281–4.465) | -6.9% |
| open_recipes_ms | 8.321 (8.087–8.651) | 8.070 (7.543–8.741) | -3.0% |
| frames: recipe view | 3.671 (3.602–3.727) | 3.133 (3.096–3.156) | -14.7% |
| search_ms | 40.867 (40.459–41.304) | 40.680 (40.487–41.043) | -0.5% |
| frames: inventory, mouse idle, history panel off | — | 3.943 (3.793–4.093) |  |

Feature checks with the mod: 43 passed, 2 failed.
- FAIL A added a bookmark
- FAIL closing the tree returns to the inventory

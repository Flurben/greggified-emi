### Dev benchmark, 60 bookmarked recipes

| Scenario | EMI alone mean (p95) ms | With Greggified EMI mean (p95) ms | Mean change |
| --- | --- | --- | --- |
| frames: inventory, mouse idle | 1.218 (1.401) | 1.062 (1.231) | -12.8% |
| frames: inventory, hover bookmark item | 1.264 (1.455) | 1.157 (1.353) | -8.5% |
| frames: inventory, hover bookmark recipe | 1.400 (1.600) | 1.162 (1.363) | -17.0% |
| frames: inventory, hover bookmark recipe + Alt | 1.402 (1.594) | 1.390 (1.593) | -0.9% |
| frames: inventory, hover index item | 1.253 (1.437) | 1.158 (1.343) | -7.6% |
| frames: inventory, hover index item + Alt | 1.259 (1.445) | 1.241 (1.429) | -1.4% |
| frames: inventory, hover chain bracket | 1.221 (1.386) | 1.258 (1.437) | +3.0% |
| frames: inventory, hover chain bracket + Shift | 1.220 (1.402) | 1.238 (1.405) | +1.5% |
| frames: uses of stick, mouse idle | 1.499 (1.694) | 1.273 (1.400) | -15.1% |
| frames: uses of stick, hover recipe slot | 1.525 (1.726) | 1.365 (1.487) | -10.5% |
| frames: uses of stick, hover recipe slot + Alt | 1.524 (1.721) | 1.545 (1.660) | +1.4% |
| action: open uses: call | 1.196 (1.591) | 1.016 (1.183) | -15.1% |
| action: open uses: first frame | 3.398 (3.799) | 1.764 (2.110) | -48.1% |
| action: open uses: call + first frame | 4.594 (5.047) | 2.780 (3.063) | -39.5% |
| action: open recipes: call | 0.906 (1.172) | 0.771 (1.047) | -14.9% |
| action: open recipes: first frame | 3.017 (3.758) | 1.594 (1.819) | -47.2% |
| action: open recipes: call + first frame | 3.923 (5.167) | 2.365 (2.757) | -39.7% |

### Dev benchmark, 600 bookmarked recipes

| Scenario | EMI alone mean (p95) ms | With Greggified EMI mean (p95) ms | Mean change |
| --- | --- | --- | --- |
| frames: inventory, mouse idle | 1.221 (1.418) | 1.132 (1.316) | -7.3% |
| frames: inventory, hover bookmark item | 1.257 (1.449) | 1.231 (1.423) | -2.1% |
| frames: inventory, hover bookmark recipe | 1.400 (1.601) | 1.232 (1.421) | -12.0% |
| frames: inventory, hover bookmark recipe + Alt | 1.383 (1.577) | 1.450 (1.659) | +4.8% |
| frames: inventory, hover index item | 1.212 (1.339) | 1.224 (1.409) | +1.0% |
| frames: inventory, hover index item + Alt | 1.212 (1.321) | 1.300 (1.508) | +7.3% |
| frames: inventory, hover chain bracket | 1.172 (1.276) | 1.334 (1.534) | +13.8% |
| frames: inventory, hover chain bracket + Shift | 1.174 (1.283) | 1.337 (1.543) | +13.9% |
| frames: uses of stick, mouse idle | 1.448 (1.581) | 1.409 (1.616) | -2.7% |
| frames: uses of stick, hover recipe slot | 1.518 (1.716) | 1.493 (1.696) | -1.6% |
| frames: uses of stick, hover recipe slot + Alt | 1.525 (1.727) | 1.644 (1.857) | +7.8% |
| action: open uses: call | 1.854 (2.214) | 1.020 (1.228) | -45.0% |
| action: open uses: first frame | 3.310 (3.479) | 1.866 (2.142) | -43.6% |
| action: open uses: call + first frame | 5.164 (5.500) | 2.886 (3.269) | -44.1% |
| action: open recipes: call | 1.515 (1.820) | 0.762 (1.020) | -49.7% |
| action: open recipes: first frame | 3.000 (3.411) | 1.675 (2.154) | -44.2% |
| action: open recipes: call + first frame | 4.515 (5.361) | 2.437 (3.174) | -46.0% |

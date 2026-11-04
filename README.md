# Remote Sender 1.1

Native Android queue helper for Bale, Eitaa and Rubika.

## Included
- XLSX import up to 1000 approved unique phone numbers.
- Mandatory `phone` and `opt_in` columns.
- Iranian phone normalization to E.164 `+98`.
- Optional image plus message text.
- Independent interval (1–1440 minutes) for Bale, Eitaa and Rubika.
- Persistent queue that survives app restarts.
- Scheduler chooses the next eligible pending task by messenger cooldown.
- Creates a deterministic local contact for the recipient.
- Opens the selected installed messenger with the text/image share intent.
- User verifies the recipient and performs the final Send action.
- MARK SENT enforces the selected messenger's interval; SKIP does not.
- Package IDs: Bale `ir.nasim`, Eitaa `ir.eitaa.messenger`, Rubika `ir.resaneh1.iptv`.

## XLSX example
| phone | opt_in |
|---|---|
| +989106119487 | yes |

Rows without explicit consent are ignored.

## Build
Java 17, Android SDK 35, Gradle 8.9:

```sh
gradle --no-daemon --stacktrace :app:assembleDebug
```

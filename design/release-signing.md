# Signed release procedure

LightReader deliberately never falls back to the debug certificate for a release build. A release
update must use the same private key that signed every already-distributed release, or Android
will reject it as an update.

## Required GitHub Secrets

Configure these repository secrets before manually running **Signed Android release** or pushing
a v* tag:

| Secret | Value |
| --- | --- |
| RELEASE_KEYSTORE_BASE64 | Base64 of the user-owned .jks or .keystore release certificate. |
| RELEASE_STORE_PASSWORD | Keystore password. |
| RELEASE_KEY_ALIAS | Release key alias. |
| RELEASE_KEY_PASSWORD | Key password. |

The workflow materializes the keystore only under the ephemeral GitHub runner temp directory, sets
the Gradle signing variables for the build step, verifies the resulting APK with apksigner, and
uploads it. A tag also creates the GitHub Release.

## Local release validation

Keep keystore.properties outside version control, or set the equivalent environment variables:

    RELEASE_STORE_FILE
    RELEASE_STORE_PASSWORD
    RELEASE_KEY_ALIAS
    RELEASE_KEY_PASSWORD

Then build and verify:

    .\gradlew.bat :app:assembleRelease
    <apksigner> verify --verbose app\build\outputs\apk\release\app-release.apk

Do not create a replacement release key merely to unblock an update. If the original signing key
is unavailable, publishing requires an explicit application-id or versioning migration decision.

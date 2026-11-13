"""Recover an unpublished tag only after the workflow verified this exact source/APK."""

import json
import os
import re
import subprocess
from urllib.error import HTTPError
from urllib.request import Request, urlopen


def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()


def require_unpublished(repository, tag, token):
    request = Request(
        f"https://api.github.com/repos/{repository}/releases/tags/{tag}",
        headers={"Authorization": f"Bearer {token}", "Accept": "application/vnd.github+json"},
    )
    try:
        with urlopen(request, timeout=30) as response:
            json.load(response)
    except HTTPError as error:
        if error.code == 404:
            return
        raise
    raise ValueError("A release already exists; refusing to move its tag")


def recover(environment):
    repository = environment["GITHUB_REPOSITORY"]
    version = environment["EXPECTED_VERSION"]
    expected = environment["RECOVER_TAG_OBJECT"]
    source = environment["GITHUB_SHA"]
    if environment["GITHUB_EVENT_NAME"] != "workflow_dispatch":
        raise ValueError("Tag recovery requires an explicit manual dispatch")
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository):
        raise ValueError("Invalid repository")
    if not re.fullmatch(r"\d+\.\d+\.\d+", version):
        raise ValueError("Recovery requires a stable semantic version")
    if not all(re.fullmatch(r"[0-9a-f]{40}", value) for value in (expected, source)):
        raise ValueError("Recovery requires exact tag-object and source SHAs")
    if git("rev-parse", "HEAD") != source:
        raise ValueError("Checked-out source differs from the verified workflow SHA")
    tag = f"v{version}"
    ref = f"refs/tags/{tag}"
    require_unpublished(repository, tag, environment["GH_TOKEN"])
    if git("ls-remote", "--refs", "origin", ref) != f"{expected}\t{ref}":
        raise ValueError("Tag changed since inspection; refusing recovery")
    git("fetch", "--no-tags", "origin", ref)
    if git("rev-parse", "FETCH_HEAD") != expected:
        raise ValueError("Tag changed while fetching; refusing recovery")
    # Only repair a descendant of the failed version, never unrelated source.
    git("merge-base", "--is-ancestor", f"{expected}^{{commit}}", source)
    git("-c", "user.name=github-actions[bot]", "-c",
        "user.email=41898282+github-actions[bot]@users.noreply.github.com",
        "tag", "--force", "--annotate", tag, source,
        "--message", f"LightReader {version}: recover unpublished release after verified build")
    new_object = git("rev-parse", ref)
    # Check again immediately before the mutation; the lease also fences concurrent tag updates.
    require_unpublished(repository, tag, environment["GH_TOKEN"])
    git("push", f"--force-with-lease={ref}:{expected}", "origin", f"{ref}:{ref}")
    if git("ls-remote", "--refs", "origin", ref) != f"{new_object}\t{ref}":
        raise ValueError("Remote tag differs from the verified recovery object")
    print(f"Recovered {tag} at verified source {source}; prior tag object {expected}")


if __name__ == "__main__":
    recover(os.environ)

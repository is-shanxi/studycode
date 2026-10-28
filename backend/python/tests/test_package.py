"""Package-level checks that validate the Phase 1 skeleton."""


def test_package_version_is_exposed(app_version: str) -> None:
    assert app_version == "0.1.0"
